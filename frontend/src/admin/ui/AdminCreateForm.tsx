import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  useCreateController,
  useNotify,
  useResourceContext,
  useResourceDefinition,
} from 'react-admin';
import { AdminCard } from './AdminCard';
import { PillButton } from './PillButton';
import { AdminBreadcrumbs } from './AdminBreadcrumbs';
import { useFormKeyboardNavigation } from './useFormKeyboardNavigation';
import { IconSave } from './icons';
import type { ReactNode } from 'react';

interface AdminCreateFormProps {
  title?: ReactNode;
  defaultValues?: Record<string, unknown>;
  children: (props: {
    record: Record<string, unknown>;
    onChange: (field: string, value: unknown) => void;
  }) => ReactNode;
  /**
   * Если передан, вызывается после успешного создания с данными ответа.
   * Компонент-родитель берёт на себя навигацию и уведомления.
   */
  onSuccessWithData?: (data: Record<string, unknown>) => void;
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

export function AdminCreateForm({
  title,
  defaultValues = {},
  children,
  onSuccessWithData,
}: AdminCreateFormProps) {
  const { save, saving } = useCreateController({ redirect: false });
  const [values, setValues] = useState<Record<string, unknown>>(defaultValues);
  const notify = useNotify();
  const navigate = useNavigate();
  const resource = useResourceContext();
  const resourceDef = useResourceDefinition();
  const resourceLabel = (resourceDef.options?.label as string) || resource || '';

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

  const formRef = useFormKeyboardNavigation(handleSave);

  return (
    <div className="p-4 lg:p-6">
      <AdminBreadcrumbs resource={resource ?? ''} resourceLabel={resourceLabel} isCreate />
      <div className="mb-4 flex items-center justify-between lg:mb-6">
        <h1 className="text-xl font-bold text-[#1a1c1e]">{title}</h1>
      </div>
      <AdminCard>
        <div ref={formRef}>{children({ record: values, onChange: handleChange })}</div>
        <div className="mt-4 flex items-center justify-between lg:mt-6">
          <PillButton icon={<IconSave size={18} />} onClick={handleSave} disabled={saving}>
            {saving ? 'Создание...' : 'Создать'}
          </PillButton>
        </div>
      </AdminCard>
    </div>
  );
}
