import { useListContext } from 'react-admin';
import { AdminListContainer } from '../ui/AdminListContainer';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { AdminEditForm } from '../ui/AdminEditForm';
import { AdminCreateForm } from '../ui/AdminCreateForm';
import { RoundedInput } from '../ui/RoundedInput';
import { formatEmpty } from '../ui/formatEmpty';
import { RowActionsMenu } from '../ui/RowActionsMenu';
import { useRowActions } from '../ui/useRowActions';
import { WORKSHOP_FILTER_FIELDS } from '../filters/configs';

interface Workshop {
  id: number;
  name: string;
  active: boolean;
}

export const WorkshopList = () => {
  const { navigateToEdit, toggleActive, deleteRecord } = useRowActions();
  const { data } = useListContext<Workshop>();
  const records = data ?? [];

  return (
    <AdminListContainer title="Цеха" records={records} filterFields={WORKSHOP_FILTER_FIELDS}>
      {({ records: filtered }) => (
        <>
          <MobileCardList
            records={filtered}
            renderCard={(workshop) => (
              <div
                className={`rounded-[20px] p-4 ${workshop.active ? 'bg-white' : 'bg-[#f8f9fa]'}`}
              >
                <div className={workshop.active ? '' : 'opacity-60'}>
                  <div className="mb-3">
                    <span className="text-base font-bold text-[#1a1c1e]">
                      {formatEmpty(workshop.name)}
                    </span>
                  </div>
                </div>
                <div className="flex items-center justify-end">
                  <RowActionsMenu
                    onEdit={() => navigateToEdit(workshop.id)}
                    isActive={workshop.active}
                    onToggleActive={() => toggleActive(workshop)}
                    onDelete={() => deleteRecord(workshop)}
                  />
                </div>
              </div>
            )}
          />
          <DesktopDataTable
            records={filtered}
            keyExtractor={(workshop) => workshop.id}
            isActive={(workshop) => workshop.active}
            columns={[
              {
                key: 'id',
                header: 'ID',
                render: (workshop) => workshop.id,
                className: 'w-16',
                filterKey: 'id',
              },
              {
                key: 'name',
                header: 'Название',
                render: (workshop) => workshop.name,
                filterKey: 'name',
              },
              {
                key: 'actions',
                header: '',
                render: (workshop) => (
                  <div className="flex items-center justify-end">
                    <RowActionsMenu
                      onEdit={() => navigateToEdit(workshop.id)}
                      isActive={workshop.active}
                      onToggleActive={() => toggleActive(workshop)}
                      onDelete={() => deleteRecord(workshop)}
                    />
                  </div>
                ),
              },
            ]}
          />
        </>
      )}
    </AdminListContainer>
  );
};

export const WorkshopEdit = () => (
  <AdminEditForm title="Редактирование цеха">
    {({ record, onChange }) => (
      <div className="space-y-5">
        <RoundedInput
          label="Название цеха"
          value={(record.name as string) ?? ''}
          onChange={(e) => onChange('name', e.target.value)}
          required
        />
      </div>
    )}
  </AdminEditForm>
);

export const WorkshopCreate = ({
  onSuccessWithData,
}: {
  onSuccessWithData?: (data: Record<string, unknown>) => void;
}) => (
  <AdminCreateForm
    title="Новый цех"
    defaultValues={{ active: true }}
    onSuccessWithData={onSuccessWithData}
  >
    {({ record, onChange }) => (
      <div className="space-y-5">
        <RoundedInput
          label="Название цеха"
          value={(record.name as string) ?? ''}
          onChange={(e) => onChange('name', e.target.value)}
          required
        />
      </div>
    )}
  </AdminCreateForm>
);
