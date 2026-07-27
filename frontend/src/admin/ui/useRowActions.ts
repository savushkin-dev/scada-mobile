import {
  useNavigate,
  useResourceContext,
  useDelete,
  useUpdate,
  useNotify,
  useRefresh,
} from 'react-admin';
import type { RaRecord } from 'react-admin';

function getErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'string') return error || fallback;
  if (error instanceof Error) return error.message || fallback;
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    return typeof message === 'string' && message ? message : fallback;
  }
  return fallback;
}

interface RecordWithActive extends RaRecord {
  active?: boolean;
}

export function useRowActions() {
  const resource = useResourceContext();
  const navigate = useNavigate();
  const notify = useNotify();
  const refresh = useRefresh();
  const [update, { isPending: updating }] = useUpdate();
  const [deleteOne, { isPending: deleting }] = useDelete();

  const navigateToEdit = (id: string | number) => {
    navigate(`${id}`);
  };

  const toggleActive = (record: RecordWithActive) => {
    if (resource == null || record.id == null) return;
    const nextActive = !record.active;
    update(
      resource,
      { id: record.id, data: { ...record, active: nextActive }, previousData: record },
      {
        onSuccess: () => {
          refresh();
          notify(nextActive ? 'Активировано' : 'Деактивировано', { type: 'info' });
        },
        onError: (error) => {
          notify(getErrorMessage(error, 'Ошибка изменения статуса'), {
            type: 'error',
            autoHideDuration: null,
          });
        },
      }
    );
  };

  const deleteRecord = (record: RaRecord) => {
    if (resource == null || record.id == null) return;
    deleteOne(
      resource,
      { id: record.id, previousData: record },
      {
        mutationMode: 'pessimistic',
        onSuccess: () => {
          refresh();
          notify('Удалено', { type: 'info' });
        },
        onError: (error) => {
          notify(getErrorMessage(error, 'Ошибка удаления'), {
            type: 'error',
            autoHideDuration: null,
          });
        },
      }
    );
  };

  return {
    navigateToEdit,
    toggleActive,
    deleteRecord,
    isBusy: updating || deleting,
  };
}

export type { RecordWithActive };
