import { useEffect, useMemo, useState } from 'react';
import {
  useEditController,
  useDelete,
  useNotify,
  useRedirect,
  useResourceContext,
} from 'react-admin';
import { AdminCard } from './AdminCard';
import { PillButton } from './PillButton';
import { ConfirmDialog } from './ConfirmDialog';
import { IconSave, IconTrash } from './icons';
import type { ReactNode } from 'react';

interface AdminEditFormProps {
  title?: ReactNode;
  children: (props: {
    record: Record<string, unknown>;
    onChange: (field: string, value: unknown) => void;
  }) => ReactNode;
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

export function AdminEditForm({ title, children }: AdminEditFormProps) {
  const { record, save, saving, isLoading } = useEditController({
    redirect: 'list',
    mutationMode: 'pessimistic',
  });
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [showDelete, setShowDelete] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const notify = useNotify();
  const redirect = useRedirect();
  const resource = useResourceContext();
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
        redirect('list');
      },
      onError: (error) => {
        notify(getErrorMessage(error, 'Ошибка сохранения'), { type: 'error' });
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
          redirect('list');
        },
        onError: (error) => {
          notify(getErrorMessage(error, 'Ошибка удаления'), { type: 'error' });
        },
      }
    );
    setShowDelete(false);
  };

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center text-[#74777f]">
        <span className="animate-pulse">Загрузка...</span>
      </div>
    );
  }

  return (
    <div className="p-4 lg:p-6">
      <AdminCard title={title}>{children({ record: values, onChange: handleChange })}</AdminCard>
      <div className="mt-4 flex items-center justify-between lg:mt-6">
        <PillButton
          icon={<IconSave size={18} />}
          onClick={handleSave}
          disabled={!isDirty || saving}
        >
          {saving ? 'Сохранение...' : 'Сохранить'}
        </PillButton>
        <PillButton
          variant="danger"
          icon={<IconTrash size={18} />}
          onClick={() => setShowDelete(true)}
          disabled={deleting}
        >
          Удалить
        </PillButton>
      </div>
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
