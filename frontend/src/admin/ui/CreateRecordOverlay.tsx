import { useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { ResourceContextProvider, useNotify } from 'react-admin';
import { IconX } from './icons';

interface CreateRecordOverlayProps {
  /** Ресурс react-admin, запись которого создаём (roles, workshops, units, ...). */
  resource: string;
  onClose: () => void;
  /**
   * Вызывается после успешного создания: передаёт id созданной записи,
   * чтобы родительский селект мог сразу её выбрать.
   */
  onCreated: (id: string | number, data: Record<string, unknown>) => void;
  /**
   * Форма создания (например, RoleCreate). Получает колбэк onSuccessWithData,
   * который нужно передать в AdminCreateForm — overlay сам покажет уведомление,
   * вернёт id в onCreated и закроется.
   */
  children: (onSuccessWithData: (data: Record<string, unknown>) => void) => ReactNode;
}

/**
 * Модальное окно создания записи справочника/автомата поверх текущей
 * страницы. Используется из селектов («не нашёл — создал — сразу выбрал»),
 * чтобы не уводить пользователя со страницы редактирования.
 *
 * Форма передаётся через children-функцию, а не импортируется здесь:
 * иначе возникает циклическая зависимость overlay ↔ ресурсы.
 */
export function CreateRecordOverlay({
  resource,
  onClose,
  onCreated,
  children,
}: CreateRecordOverlayProps) {
  const notify = useNotify();

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const handleSuccess = (data: Record<string, unknown>) => {
    notify('Создано', { type: 'info' });
    onCreated(data.id as string | number, data);
    onClose();
  };

  return createPortal(
    <div
      className="fixed inset-0 z-[70] flex items-center justify-center bg-black/40 p-4 backdrop-blur-[2px]"
      onClick={onClose}
      role="presentation"
    >
      <div
        role="dialog"
        aria-modal="true"
        className="relative flex h-[85vh] w-full max-w-4xl flex-col overflow-hidden rounded-[20px] bg-[#f8f9fa] shadow-[0_30px_80px_rgba(17,24,39,0.25)]"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          onClick={onClose}
          aria-label="Закрыть"
          className="absolute right-4 top-4 z-10 flex h-9 w-9 items-center justify-center rounded-full border border-[#e8eaed] bg-white text-[#74777f] transition-colors hover:bg-[#f8f9fa] hover:text-[#1a1c1e]"
        >
          <IconX size={16} />
        </button>
        <ResourceContextProvider value={resource}>
          {children(handleSuccess)}
        </ResourceContextProvider>
      </div>
    </div>,
    document.body
  );
}
