import { useEffect, useRef, useState, type ReactNode } from 'react';
import { BottomSheet } from './BottomSheet';
import { AdminChip } from './AdminChip';
import { IconSearch, IconX, IconCheck } from './icons';

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
}: SearchableSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [isMobile, setIsMobile] = useState(false);
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

  const removeValue = (id: string | number) => {
    if (!multiple) return;
    const next = normalizedValue.filter((v) => String(v) !== String(id));
    onChange(next.length > 0 ? next : null);
  };

  const fieldClasses =
    'min-h-[48px] w-full cursor-pointer rounded-[14px] border-[1.5px] bg-white px-4 py-2.5 text-[15px] text-[#1a1c1e] ' +
    'outline-none transition-all duration-200 ' +
    (error ? 'border-[#ea4335] ' : 'border-[#e8eaed] hover:border-[#c4c7cc] ') +
    (disabled ? 'cursor-not-allowed bg-[#f8f9fa] text-[#74777f] ' : ' ');

  const trigger = (
    <div ref={containerRef} onClick={() => !disabled && setIsOpen(true)} className={fieldClasses}>
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

  const optionList = (
    <div className="max-h-[50vh] overflow-y-auto">
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
          {optionList}
        </BottomSheet>
      ) : (
        isOpen && (
          <div className="fixed inset-0 z-40" onClick={() => setIsOpen(false)}>
            <div
              className="absolute z-50 rounded-[14px] border border-[#e8eaed] bg-white p-2 shadow-[0_4px_16px_rgba(0,0,0,0.08)]"
              style={{
                top: containerRef.current?.getBoundingClientRect().bottom ?? 0 + 4,
                left: containerRef.current?.getBoundingClientRect().left ?? 0,
                width: containerRef.current?.getBoundingClientRect().width ?? 300,
              }}
              onClick={(e) => e.stopPropagation()}
            >
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
              </div>
              {optionList}
            </div>
          </div>
        )
      )}
    </div>
  );
}
