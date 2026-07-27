import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useEditController, useDelete, useNotify, useResourceContext } from 'react-admin';
import { AdminCard } from './AdminCard';
import { PillButton } from './PillButton';
import { ConfirmDialog } from './ConfirmDialog';
import { AdminPageHeader } from './AdminPageHeader';
import { ActionMenu } from './ActionMenu';
import { ResizablePanels } from './ResizablePanels';
import { useFormKeyboardNavigation } from './useFormKeyboardNavigation';
import { IconSave } from './icons';
import type { ReactNode } from 'react';

interface AdminEditFormProps {
  title?: ReactNode;
  layout?: 'single' | 'two-column';
  children:
    | ReactNode
    | ((props: {
        record: Record<string, unknown>;
        onChange: (field: string, value: unknown) => void;
        slot?: 'left' | 'right';
      }) => ReactNode);
  /** Дополнительные действия рядом с кнопкой "Сохранить" (только для single layout). */
  extraActions?: ReactNode | ((record: Record<string, unknown>) => ReactNode);
  /** Заголовок левой карточки (для two-column). */
  leftCardTitle?: ReactNode;
  /** Заголовок правой карточки (для two-column). */
  rightCardTitle?: ReactNode;
  /** Иконка левой карточки (для two-column). */
  leftCardIcon?: ReactNode;
  /** Иконка правой карточки (для two-column). */
  rightCardIcon?: ReactNode;
}

function isRecordDirty(
  original: Record<string, unknown> | undefined,
  current: Record<string, unknown>
): boolean {
  if (!original) return true;
  return Object.entries(current).some(([key, value]) => original[key] !== value);
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

export function AdminEditForm({
  title,
  layout = 'single',
  children,
  extraActions,
  leftCardTitle = 'Основная информация',
  rightCardTitle = 'Дополнительная информация',
  leftCardIcon,
  rightCardIcon,
}: AdminEditFormProps) {
  const { record, save, saving, isLoading } = useEditController({
    redirect: false,
    mutationMode: 'pessimistic',
  });
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [showDelete, setShowDelete] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const notify = useNotify();
  const navigate = useNavigate();
  const resource = useResourceContext();
  const [deleteOne] = useDelete(undefined, undefined, {
    mutationMode: 'pessimistic',
  });

  useEffect(() => {
    setValues(record ?? {});
  }, [record]);

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < 1024);
    check();
    window.addEventListener('resize', check);
    return () => window.removeEventListener('resize', check);
  }, []);

  const isDirty = useMemo(() => isRecordDirty(record, values), [record, values]);

  const handleChange = (field: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [field]: value }));
  };

  const handleSave = () => {
    if (!isDirty) return;
    save?.(values, {
      onSuccess: () => {
        notify('Сохранено', { type: 'info' });
        navigate(getListPath(resource ?? ''));
      },
      onError: (error) => {
        notify(getErrorMessage(error, 'Ошибка сохранения'), {
          type: 'error',
          autoHideDuration: null,
        });
      },
    });
  };

  const handleConfirmDelete = () => {
    if (!values.id) return;
    deleteOne(
      resource ?? '',
      { id: values.id, previousData: record },
      {
        onSuccess: () => {
          notify('Удалено', { type: 'info' });
          navigate(getListPath(resource ?? ''));
        },
        onError: (error) => {
          notify(getErrorMessage(error, 'Ошибка удаления'), {
            type: 'error',
            autoHideDuration: null,
          });
        },
      }
    );
    setShowDelete(false);
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

  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center text-[#74777f]">
        <span className="animate-pulse">Загрузка...</span>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col p-3 lg:p-4">
      <AdminPageHeader
        title={title}
        actions={
          <>
            <PillButton
              icon={<IconSave size={18} />}
              onClick={handleSave}
              disabled={!isDirty || saving}
              className="h-9 px-3"
            >
              {saving ? 'Сохранение...' : 'Сохранить'}
            </PillButton>
            <ActionMenu
              items={[
                {
                  key: 'delete',
                  label: 'Удалить',
                  variant: 'danger',
                  onClick: () => setShowDelete(true),
                },
              ]}
            />
          </>
        }
      />

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
            </AdminCard>
          }
        />
      ) : (
        <AdminCard className="flex min-h-0 flex-1 flex-col overflow-hidden">
          <div ref={formRef} className="flex-1 overflow-y-auto">
            {renderChildren()}
          </div>
          <div className="mt-3 flex items-center justify-between border-t border-[#f0f0f0] pt-3 lg:mt-4">
            <div className="flex items-center gap-2">
              <PillButton
                icon={<IconSave size={18} />}
                onClick={handleSave}
                disabled={!isDirty || saving}
              >
                {saving ? 'Сохранение...' : 'Сохранить'}
              </PillButton>
              {typeof extraActions === 'function' ? extraActions(values) : extraActions}
            </div>
          </div>
        </AdminCard>
      )}

      <ConfirmDialog
        isOpen={showDelete}
        onClose={() => setShowDelete(false)}
        onConfirm={handleConfirmDelete}
        title="Удалить запись?"
        message="Это действие нельзя отменить."
        confirmText="Удалить"
        isMobile={isMobile}
      />
    </div>
  );
}
