import { useEffect, useState } from 'react';
import { useEditController, useDeleteController, useNotify } from 'react-admin';
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

export function AdminEditForm({ title, children }: AdminEditFormProps) {
  const { record, save, saving, isLoading } = useEditController();
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [showDelete, setShowDelete] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const notify = useNotify();
  const { isPending: deleting, handleDelete } = useDeleteController({
    record: values as { id: string | number },
    mutationMode: 'pessimistic',
    mutationOptions: {
      onSuccess: () => notify('Удалено', { type: 'info' }),
      onError: () => notify('Ошибка удаления', { type: 'error' }),
    },
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

  const handleChange = (field: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [field]: value }));
  };

  const handleSave = () => {
    save?.(values, {
      onSuccess: () => notify('Сохранено', { type: 'info' }),
      onError: () => notify('Ошибка сохранения', { type: 'error' }),
    });
  };

  const handleConfirmDelete = () => {
    handleDelete();
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
        <PillButton icon={<IconSave size={18} />} onClick={handleSave} disabled={saving}>
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
