import { useState } from 'react';
import { useCreateController, useNotify } from 'react-admin';
import { AdminCard } from './AdminCard';
import { PillButton } from './PillButton';
import { IconSave } from './icons';
import type { ReactNode } from 'react';

interface AdminCreateFormProps {
  title?: ReactNode;
  defaultValues?: Record<string, unknown>;
  children: (props: {
    record: Record<string, unknown>;
    onChange: (field: string, value: unknown) => void;
  }) => ReactNode;
}

export function AdminCreateForm({ title, defaultValues = {}, children }: AdminCreateFormProps) {
  const { save, saving } = useCreateController();
  const [values, setValues] = useState<Record<string, unknown>>(defaultValues);
  const notify = useNotify();

  const handleChange = (field: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [field]: value }));
  };

  const handleSave = () => {
    save?.(values, {
      onSuccess: () => notify('Создано', { type: 'info' }),
      onError: () => notify('Ошибка создания', { type: 'error' }),
    });
  };

  return (
    <div className="p-4 lg:p-6">
      <AdminCard title={title}>{children({ record: values, onChange: handleChange })}</AdminCard>
      <div className="mt-4 flex items-center justify-between lg:mt-6">
        <PillButton icon={<IconSave size={18} />} onClick={handleSave} disabled={saving}>
          {saving ? 'Создание...' : 'Создать'}
        </PillButton>
      </div>
    </div>
  );
}
