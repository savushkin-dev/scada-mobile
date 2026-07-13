import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCreateController, useNotify, useResourceContext } from 'react-admin';
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

function getErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'string') return error || fallback;
  if (error instanceof Error) return error.message || fallback;
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    return typeof message === 'string' && message ? message : fallback;
  }
  return fallback;
}

export function AdminCreateForm({ title, defaultValues = {}, children }: AdminCreateFormProps) {
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
      onSuccess: () => {
        notify('Создано', { type: 'info' });
        navigate('/admin/' + resource);
      },
      onError: (error) => {
        notify(getErrorMessage(error, 'Ошибка создания'), {
          type: 'error',
          autoHideDuration: null,
        });
      },
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
