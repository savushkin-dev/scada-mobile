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
import { DEVICE_TYPE_FILTER_FIELDS } from '../filters/configs';

interface DeviceType {
  id: number;
  code: string;
  name: string;
}

export const DeviceTypeList = () => {
  const { navigateToEdit, deleteRecord } = useRowActions();
  const { data } = useListContext<DeviceType>();
  const records = data ?? [];

  return (
    <AdminListContainer
      title="Типы устройств"
      records={records}
      filterFields={DEVICE_TYPE_FILTER_FIELDS}
    >
      {({ records: filtered }) => (
        <>
          <MobileCardList
            records={filtered}
            renderCard={(type) => (
              <div className="rounded-[20px] bg-white p-4">
                <div className="mb-1">
                  <span className="text-base font-bold text-[#1a1c1e]">
                    {formatEmpty(type.name)}
                  </span>
                </div>
                <div className="mb-3 text-sm text-[#74777f]">{formatEmpty(type.code)}</div>
                <div className="flex items-center justify-end">
                  <RowActionsMenu
                    onEdit={() => navigateToEdit(type.id)}
                    onDelete={() => deleteRecord(type)}
                  />
                </div>
              </div>
            )}
          />
          <DesktopDataTable
            records={filtered}
            keyExtractor={(type) => type.id}
            columns={[
              {
                key: 'id',
                header: 'ID',
                render: (type) => type.id,
                className: 'w-16',
                filterKey: 'id',
              },
              { key: 'code', header: 'Код', render: (type) => type.code, filterKey: 'code' },
              { key: 'name', header: 'Название', render: (type) => type.name, filterKey: 'name' },
              {
                key: 'actions',
                header: '',
                render: (type) => (
                  <div className="flex items-center justify-end">
                    <RowActionsMenu
                      onEdit={() => navigateToEdit(type.id)}
                      onDelete={() => deleteRecord(type)}
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

export const DeviceTypeEdit = () => (
  <AdminEditForm title="Редактирование типа устройства">
    {({ record, onChange }) => (
      <div className="space-y-5">
        <RoundedInput
          label="Код типа"
          value={(record.code as string) ?? ''}
          onChange={(e) => onChange('code', e.target.value)}
          required
        />
        <RoundedInput
          label="Название типа"
          value={(record.name as string) ?? ''}
          onChange={(e) => onChange('name', e.target.value)}
          required
        />
      </div>
    )}
  </AdminEditForm>
);

export const DeviceTypeCreate = ({
  onSuccessWithData,
}: {
  onSuccessWithData?: (data: Record<string, unknown>) => void;
}) => (
  <AdminCreateForm title="Новый тип устройства" onSuccessWithData={onSuccessWithData}>
    {({ record, onChange }) => (
      <div className="space-y-5">
        <RoundedInput
          label="Код типа"
          value={(record.code as string) ?? ''}
          onChange={(e) => onChange('code', e.target.value)}
          required
        />
        <RoundedInput
          label="Название типа"
          value={(record.name as string) ?? ''}
          onChange={(e) => onChange('name', e.target.value)}
          required
        />
      </div>
    )}
  </AdminCreateForm>
);
