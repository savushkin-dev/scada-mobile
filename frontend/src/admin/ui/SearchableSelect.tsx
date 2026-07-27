import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { BottomSheet } from './BottomSheet';
import { AdminChip } from './AdminChip';
import { IconSearch, IconX, IconCheck, IconPlus } from './icons';

/** Отступ дропдауна от поля-триггера. */
const DROPDOWN_GAP = 6;
/** Если снизу меньше этого порога — пробуем открыть дропдаун вверх. */
const DROPDOWN_FLIP_THRESHOLD = 260;
/** Минимальная/максимальная высота десктопного дропдауна. */
const DROPDOWN_MIN_HEIGHT = 140;
const DROPDOWN_MAX_HEIGHT = 400;

interface Choice {
  id: string | number;
  label: string;
  disabled?: boolean;
  suffix?: ReactNode;
}

interface SearchableSelectProps {
  label?: ReactNode;
  value: string | number | (string | number)[] | null;
  options: Choice[];
  onChange: (value: string | number | (string | number)[] | null) => void;
  placeholder?: string;
  multiple?: boolean;
  disabled?: boolean;
  error?: string;
  hint?: string;
  onAddNew?: () => void;
  addNewLabel?: string;
}

export function SearchableSelect({
  label,
  value,
  options,
  onChange,
  placeholder = 'Выберите...',
  multiple = false,
  disabled = false,
  error,
  hint,
  onAddNew,
  addNewLabel = 'Добавить',
}: SearchableSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [isMobile, setIsMobile] = useState(false);
  const [triggerRect, setTriggerRect] = useState<DOMRect | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < 1024);
    check();
    window.addEventListener('resize', check);
    return () => window.removeEventListener('resize', check);
  }, []);

  useEffect(() => {
    if (!isOpen) return;
    setSearch('');
  }, [isOpen]);

  // Пока дропдаун открыт, следим за позицией поля-триггера:
  // страница может скроллиться (в т.ч. внутри карточек), дропдаун fixed —
  // без этого он «отлипнет» от поля.
  useEffect(() => {
    if (!isOpen || isMobile) return;
    const updateRect = () => {
      if (containerRef.current) {
        setTriggerRect(containerRef.current.getBoundingClientRect());
      }
    };
    updateRect();
    window.addEventListener('resize', updateRect);
    window.addEventListener('scroll', updateRect, true);
    return () => {
      window.removeEventListener('resize', updateRect);
      window.removeEventListener('scroll', updateRect, true);
    };
  }, [isOpen, isMobile]);

  const normalizedValue: (string | number)[] =
    value == null
      ? []
      : Array.isArray(value)
        ? (value as (string | number)[])
        : [value as string | number];

  const selectedIds = new Set(normalizedValue.map((v) => String(v)));

  const selectedOptions = options.filter((o) => selectedIds.has(String(o.id)));

  const filteredOptions = options.filter((o) =>
    o.label.toLowerCase().includes(search.toLowerCase())
  );

  const toggleOption = (id: string | number) => {
    if (multiple) {
      const next = selectedIds.has(String(id))
        ? normalizedValue.filter((v) => String(v) !== String(id))
        : [...normalizedValue, id];
      onChange(next.length > 0 ? next : null);
    } else {
      onChange(id);
      setIsOpen(false);
    }
  };

  // Направление и высота дропдауна: если снизу мало места,
  // а сверху больше — открываем вверх; высота ограничена доступным местом.
  const placement = useMemo(() => {
    if (!triggerRect) return null;
    const viewportHeight = window.innerHeight;
    const spaceBelow = viewportHeight - triggerRect.bottom - DROPDOWN_GAP;
    const spaceAbove = triggerRect.top - DROPDOWN_GAP;
    const openUp = spaceBelow < DROPDOWN_FLIP_THRESHOLD && spaceAbove > spaceBelow;
    const available = openUp ? spaceAbove : spaceBelow;
    const maxHeight = Math.max(DROPDOWN_MIN_HEIGHT, Math.min(available, DROPDOWN_MAX_HEIGHT));
    return { openUp, maxHeight };
  }, [triggerRect]);

  const removeValue = (id: string | number) => {
    if (!multiple) return;
    const next = normalizedValue.filter((v) => String(v) !== String(id));
    onChange(next.length > 0 ? next : null);
  };

  const handleAddNew = () => {
    setIsOpen(false);
    onAddNew?.();
  };

  const fieldClasses =
    'min-h-[48px] w-full cursor-pointer rounded-[14px] border-[1.5px] bg-white px-4 py-2.5 text-[15px] text-[#1a1c1e] ' +
    'outline-none transition-all duration-200 ' +
    (error ? 'border-[#ea4335] ' : 'border-[#e8eaed] hover:border-[#c4c7cc] ') +
    (disabled ? 'cursor-not-allowed bg-[#f8f9fa] text-[#74777f] ' : ' ');

  const trigger = (
    <div
      ref={containerRef}
      onClick={() => {
        if (disabled) return;
        if (containerRef.current) {
          setTriggerRect(containerRef.current.getBoundingClientRect());
        }
        setIsOpen(true);
      }}
      className={fieldClasses}
    >
      {selectedOptions.length === 0 ? (
        <span className="text-[#74777f]">{placeholder}</span>
      ) : (
        <div className="flex flex-wrap items-center gap-1.5">
          {selectedOptions.map((o) =>
            multiple ? (
              <AdminChip key={o.id} onRemove={() => removeValue(o.id)}>
                {o.label}
              </AdminChip>
            ) : (
              <span key={o.id} className="font-medium">
                {o.label}
              </span>
            )
          )}
        </div>
      )}
    </div>
  );

  const searchInput = (
    <div className="mb-2 flex items-center gap-2 rounded-[12px] border border-[#e8eaed] bg-[#f8f9fa] px-3 py-2">
      <IconSearch size={16} className="text-[#74777f]" />
      <input
        type="text"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Поиск..."
        className="flex-1 bg-transparent text-sm text-[#1a1c1e] outline-none placeholder:text-[#74777f]"
        autoFocus
      />
      {search && (
        <button type="button" onClick={() => setSearch('')} className="text-[#74777f]">
          <IconX size={16} />
        </button>
      )}
    </div>
  );

  const addNewButton = onAddNew ? (
    <button
      type="button"
      onClick={handleAddNew}
      className="flex w-full items-center justify-center gap-2 rounded-[12px] bg-[#1a1c1e] py-2.5 text-sm font-semibold text-white transition-colors hover:bg-[#2c2f33]"
    >
      <IconPlus size={16} />
      {addNewLabel}
    </button>
  ) : null;

  const optionList = (
    <div className="-mr-1 min-h-0 flex-1 overflow-y-auto pr-1">
      {filteredOptions.length === 0 && (
        <div className="py-6 text-center text-sm text-[#74777f]">Ничего не найдено</div>
      )}
      {filteredOptions.map((option) => {
        const isSelected = selectedIds.has(String(option.id));
        return (
          <button
            key={option.id}
            type="button"
            disabled={option.disabled}
            onClick={() => toggleOption(option.id)}
            className={
              'flex w-full items-center justify-between rounded-[12px] px-3 py-3 text-left text-[15px] transition-colors ' +
              (isSelected ? 'bg-[#f0f7ff] text-[#4285f4] ' : 'text-[#1a1c1e] hover:bg-[#f8f9fa] ') +
              (option.disabled ? 'cursor-not-allowed opacity-50 ' : ' ')
            }
          >
            <span className="flex items-center gap-2">
              {option.label}
              {option.suffix}
            </span>
            {isSelected && <IconCheck size={18} className="text-[#4285f4]" />}
          </button>
        );
      })}
    </div>
  );

  const dropdownContent = (
    <div
      className="flex flex-col gap-2"
      style={{ maxHeight: placement?.maxHeight ?? DROPDOWN_MAX_HEIGHT }}
    >
      {searchInput}
      {optionList}
      {addNewButton}
    </div>
  );

  const mobileSearchInput = (
    <div className="mb-3 flex items-center gap-2 rounded-[14px] border border-[#e8eaed] bg-[#f8f9fa] px-3 py-2.5">
      <IconSearch size={18} className="text-[#74777f]" />
      <input
        type="text"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Поиск..."
        className="flex-1 bg-transparent text-[15px] text-[#1a1c1e] outline-none placeholder:text-[#74777f]"
        autoFocus
      />
      {search && (
        <button type="button" onClick={() => setSearch('')} className="text-[#74777f]">
          <IconX size={16} />
        </button>
      )}
    </div>
  );

  const mobileContent = (
    <div className="flex max-h-[60vh] flex-col gap-2">
      {mobileSearchInput}
      {optionList}
      {addNewButton}
    </div>
  );

  return (
    <div className="w-full">
      {label && (
        <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
          {label}
        </label>
      )}
      {trigger}
      {error && <p className="mt-1.5 text-xs text-[#ea4335]">{error}</p>}
      {hint && !error && <p className="mt-1.5 text-xs text-[#74777f]">{hint}</p>}

      {isMobile ? (
        <BottomSheet isOpen={isOpen} onClose={() => setIsOpen(false)} title={label}>
          {mobileContent}
        </BottomSheet>
      ) : (
        isOpen && (
          <div className="fixed inset-0 z-40" onClick={() => setIsOpen(false)}>
            <div
              className="absolute z-50 rounded-[14px] border border-[#e8eaed] bg-white p-2 shadow-[0_4px_16px_rgba(0,0,0,0.08)]"
              style={{
                left: triggerRect?.left ?? 0,
                width: triggerRect?.width ?? 300,
                ...(placement?.openUp
                  ? { bottom: window.innerHeight - (triggerRect?.top ?? 0) + DROPDOWN_GAP }
                  : { top: (triggerRect?.bottom ?? 0) + DROPDOWN_GAP }),
              }}
              onClick={(e) => e.stopPropagation()}
            >
              {dropdownContent}
            </div>
          </div>
        )
      )}
    </div>
  );
}
