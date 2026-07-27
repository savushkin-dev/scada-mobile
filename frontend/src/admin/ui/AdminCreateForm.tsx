import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCreateController, useNotify, useResourceContext } from 'react-admin';
import { AdminCard } from './AdminCard';
import { PillButton } from './PillButton';
import { AdminPageHeader } from './AdminPageHeader';
import { ResizablePanels } from './ResizablePanels';
import { useFormKeyboardNavigation } from './useFormKeyboardNavigation';
import { IconSave } from './icons';
import type { ReactNode } from 'react';

interface AdminCreateFormProps {
  title?: ReactNode;
  layout?: 'single' | 'two-column';
  defaultValues?: Record<string, unknown>;
  children:
    | ReactNode
    | ((props: {
        record: Record<string, unknown>;
        onChange: (field: string, value: unknown) => void;
        slot?: 'left' | 'right';
      }) => ReactNode);
  /**
   * Если передан, вызывается после успешного создания с данными ответа.
   * Компонент-родитель берёт на себя навигацию и уведомления.
   */
  onSuccessWithData?: (data: Record<string, unknown>) => void;
  /** Заголовок левой карточки (для two-column). */
  leftCardTitle?: ReactNode;
  /** Заголовок правой карточки (для two-column). */
  rightCardTitle?: ReactNode;
  /** Иконка левой карточки (для two-column). */
  leftCardIcon?: ReactNode;
  /** Иконка правой карточки (для two-column). */
  rightCardIcon?: ReactNode;
}

function getErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'string') return error || fallback;
  if (error instanceof Error) return error.message || fallback;
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    return typeof message === 'string' && message ? message : fallback;
  }
  return fallback;
}

function getListPath(resource: string): string {
  const referenceNames = ['roles', 'workshops', 'device-types', 'device-catalog'];
  if (referenceNames.includes(resource)) {
    return `/admin/settings/references/${resource}`;
  }
  return `/admin/${resource}`;
}

export function AdminCreateForm({
  title,
  layout = 'single',
  defaultValues = {},
  children,
  onSuccessWithData,
  leftCardTitle = 'Основная информация',
  rightCardTitle = 'Дополнительная информация',
  leftCardIcon,
  rightCardIcon,
}: AdminCreateFormProps) {
  const { save, saving } = useCreateController({ redirect: false });
  const [values, setValues] = useState<Record<string, unknown>>(defaultValues);
  const notify = useNotify();
  const navigate = useNavigate();
  const resource = useResourceContext();

  const handleChange = (field: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [field]: value }));
  };

  const handleSave = () => {
    save?.(values, {
      onSuccess: (response: unknown) => {
        if (onSuccessWithData) {
          const data =
            response && typeof response === 'object' && 'data' in response
              ? (response as { data: Record<string, unknown> }).data
              : (response as Record<string, unknown>);
          onSuccessWithData(data ?? {});
          return;
        }
        notify('Создано', { type: 'info' });
        navigate(getListPath(resource ?? ''));
      },
      onError: (error) => {
        notify(getErrorMessage(error, 'Ошибка создания'), {
          type: 'error',
          autoHideDuration: null,
        });
      },
    });
  };

  const formRef = useFormKeyboardNavigation(handleSave);

  const renderChildren = (slot?: 'left' | 'right') => {
    if (typeof children === 'function') {
      return (children as (props: Record<string, unknown>) => ReactNode)({
        record: values,
        onChange: handleChange,
        slot,
      });
    }
    return children;
  };

  return (
    <div className="flex h-full flex-col p-3 lg:p-4">
      <AdminPageHeader title={title} />

      {layout === 'two-column' ? (
        <ResizablePanels
          left={
            <AdminCard
              className="flex min-h-0 flex-1 flex-col overflow-hidden"
              title={leftCardTitle}
              icon={leftCardIcon}
            >
              <div ref={formRef} className="flex-1 overflow-y-auto">
                <div className="space-y-4">{renderChildren('left')}</div>
              </div>
            </AdminCard>
          }
          right={
            <AdminCard
              className="flex min-h-0 flex-1 flex-col overflow-hidden"
              title={rightCardTitle}
              icon={rightCardIcon}
            >
              <div className="flex-1 overflow-y-auto">
                <div className="space-y-4">{renderChildren('right')}</div>
              </div>
              <div className="mt-3 flex items-center justify-end border-t border-[#f0f0f0] pt-3 lg:mt-4">
                <PillButton icon={<IconSave size={18} />} onClick={handleSave} disabled={saving}>
                  {saving ? 'Создание...' : 'Создать'}
                </PillButton>
              </div>
            </AdminCard>
          }
        />
      ) : (
        <AdminCard className="flex min-h-0 flex-1 flex-col overflow-hidden">
          <div ref={formRef} className="flex-1 overflow-y-auto">
            {renderChildren()}
          </div>
          <div className="mt-3 flex items-center justify-between border-t border-[#f0f0f0] pt-3 lg:mt-4">
            <PillButton icon={<IconSave size={18} />} onClick={handleSave} disabled={saving}>
              {saving ? 'Создание...' : 'Создать'}
            </PillButton>
          </div>
        </AdminCard>
      )}
    </div>
  );
}
