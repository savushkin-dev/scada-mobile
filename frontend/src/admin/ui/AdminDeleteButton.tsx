import { useState } from 'react';
import { useDelete, useRefresh, useResourceContext } from 'react-admin';
import { PillButton } from './PillButton';
import { ConfirmDialog } from './ConfirmDialog';
import { IconTrash } from './icons';

interface AdminDeleteButtonProps {
  record: { id: string | number };
  className?: string;
  size?: 'default' | 'small';
}

export function AdminDeleteButton({
  record,
  className = '',
  size = 'small',
}: AdminDeleteButtonProps) {
  const resource = useResourceContext();
  const refresh = useRefresh();
  const [isOpen, setIsOpen] = useState(false);
  const [deleteOne, { isPending }] = useDelete(
    resource,
    { id: record.id, previousData: record },
    {
      mutationMode: 'pessimistic',
      onSuccess: () => {
        setIsOpen(false);
        refresh();
      },
      onError: () => {
        setIsOpen(false);
      },
    }
  );

  const handleConfirm = () => {
    deleteOne();
  };

  const isSmall = size === 'small';

  return (
    <>
      <PillButton
        variant="danger"
        icon={<IconTrash size={isSmall ? 16 : 18} />}
        onClick={() => setIsOpen(true)}
        disabled={isPending}
        className={isSmall ? `h-9 px-3 text-xs ${className}` : className}
      >
        Удалить
      </PillButton>
      <ConfirmDialog
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        onConfirm={handleConfirm}
        title="Удалить запись?"
        message="Это действие нельзя отменить."
        confirmText="Удалить"
      />
    </>
  );
}
