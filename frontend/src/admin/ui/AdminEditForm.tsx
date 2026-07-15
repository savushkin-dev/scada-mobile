import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  useEditController,
  useDelete,
  useNotify,
  useResourceContext,
  useResourceDefinition,
} from 'react-admin';
import { AdminCard } from './AdminCard';
import { PillButton } from './PillButton';
import { ConfirmDialog } from './ConfirmDialog';
import { AdminBreadcrumbs } from './AdminBreadcrumbs';
import { useFormKeyboardNavigation } from './useFormKeyboardNavigation';
import { IconSave, IconTrash } from './icons';
import type { ReactNode } from 'react';

interface AdminEditFormProps {
  title?: ReactNode;
  children: (props: {
    record: Record<string, unknown>;
    onChange: (field: string, value: unknown) => void;
  }) => ReactNode;
  /** Дополнительные действия рядом с кнопкой "Сохранить". */
  extraActions?: ReactNode;
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

function getRecordName(record: Record<string, unknown>): string {
  if (record.name) return String(record.name);
  if (record.fullName) return String(record.fullName);
  if (record.code) return String(record.code);
  return String(record.id ?? '');
}

export function AdminEditForm({ title, children, extraActions }: AdminEditFormProps) {
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
  const resourceDef = useResourceDefinition();
  const resourceLabel = (resourceDef.options?.label as string) || resource || '';
  const [deleteOne, { isPending: deleting }] = useDelete(undefined, undefined, {
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
        navigate('/admin/' + resource);
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
          navigate('/admin/' + resource);
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

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center text-[#74777f]">
        <span className="animate-pulse">Загрузка...</span>
      </div>
    );
  }

  return (
    <div className="p-4 lg:p-6">
      <AdminBreadcrumbs
        resource={resource ?? ''}
        resourceLabel={resourceLabel}
        recordName={getRecordName(values)}
      />
      <div className="mb-4 flex items-center justify-between lg:mb-6">
        <h1 className="text-xl font-bold text-[#1a1c1e]">{title}</h1>
      </div>
      <AdminCard>
        <div ref={formRef}>{children({ record: values, onChange: handleChange })}</div>
        <div className="mt-4 flex items-center justify-between lg:mt-6">
          <div className="flex items-center gap-2">
            <PillButton
              icon={<IconSave size={18} />}
              onClick={handleSave}
              disabled={!isDirty || saving}
            >
              {saving ? 'Сохранение...' : 'Сохранить'}
            </PillButton>
            {extraActions}
          </div>
          <PillButton
            variant="danger"
            icon={<IconTrash size={18} />}
            onClick={() => setShowDelete(true)}
            disabled={deleting}
          >
            Удалить
          </PillButton>
        </div>
      </AdminCard>
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
