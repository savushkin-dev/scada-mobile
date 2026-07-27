import { useState } from 'react';
import { useListContext } from 'react-admin';
import { AdminListContainer } from '../ui/AdminListContainer';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { AdminEditForm } from '../ui/AdminEditForm';
import { AdminCreateForm } from '../ui/AdminCreateForm';
import { CreateRecordOverlay } from '../ui/CreateRecordOverlay';
import { DeviceTypeCreate } from './DeviceTypes';
import { RoundedInput } from '../ui/RoundedInput';
import { ReferenceSelect } from '../ui/ReferenceSelect';
import { formatEmpty } from '../ui/formatEmpty';
import { useNameMap } from '../ui/useNameMap';
import { RowActionsMenu } from '../ui/RowActionsMenu';
import { useRowActions } from '../ui/useRowActions';

interface DeviceCatalogItem {
  id: number;
  code: string;
  name: string;
  typeId?: number | null;
  active: boolean;
}

export const DeviceCatalogList = () => {
  const { navigateToEdit, toggleActive, deleteRecord } = useRowActions();
  const { data } = useListContext<DeviceCatalogItem>();
  const records = data ?? [];
  const getDeviceTypeName = useNameMap('device-types');

  return (
    <AdminListContainer
      title="Справочник устройств"
      records={records}
      searchableFields={['code', 'name']}
    >
      {({ records: filtered }) => (
        <>
          <MobileCardList
            records={filtered}
            renderCard={(item) => (
              <div className={`rounded-[20px] p-4 ${item.active ? 'bg-white' : 'bg-[#f8f9fa]'}`}>
                <div className={item.active ? '' : 'opacity-60'}>
                  <div className="mb-1">
                    <span className="text-base font-bold text-[#1a1c1e]">
                      {formatEmpty(item.name)}
                    </span>
                  </div>
                  <div className="mb-3 text-sm text-[#74777f]">{formatEmpty(item.code)}</div>
                  <div className="mb-3 text-sm text-[#74777f]">
                    {formatEmpty(item.typeId ? getDeviceTypeName(item.typeId) : null)}
                  </div>
                </div>
                <div className="flex items-center justify-end">
                  <RowActionsMenu
                    onEdit={() => navigateToEdit(item.id)}
                    isActive={item.active}
                    onToggleActive={() => toggleActive(item)}
                    onDelete={() => deleteRecord(item)}
                  />
                </div>
              </div>
            )}
          />
          <DesktopDataTable
            records={filtered}
            keyExtractor={(item) => item.id}
            isActive={(item) => item.active}
            columns={[
              { key: 'id', header: 'ID', render: (item) => item.id, className: 'w-12' },
              { key: 'code', header: 'Код', render: (item) => item.code },
              { key: 'name', header: 'Название', render: (item) => item.name },
              {
                key: 'type',
                header: 'Тип',
                render: (item) => (
                  <span className="text-[#1a1c1e]">
                    {formatEmpty(item.typeId ? getDeviceTypeName(item.typeId) : null)}
                  </span>
                ),
              },
              {
                key: 'actions',
                header: '',
                render: (item) => (
                  <div className="flex items-center justify-end">
                    <RowActionsMenu
                      onEdit={() => navigateToEdit(item.id)}
                      isActive={item.active}
                      onToggleActive={() => toggleActive(item)}
                      onDelete={() => deleteRecord(item)}
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

function DeviceCatalogFormFields({
  record,
  onChange,
}: {
  record: Record<string, unknown>;
  onChange: (field: string, value: unknown) => void;
}) {
  const [creatingType, setCreatingType] = useState(false);

  return (
    <div className="space-y-5">
      <RoundedInput
        label="Код устройства"
        value={(record.code as string) ?? ''}
        onChange={(e) => onChange('code', e.target.value)}
        required
      />
      <RoundedInput
        label="Название"
        value={(record.name as string) ?? ''}
        onChange={(e) => onChange('name', e.target.value)}
        required
      />
      <ReferenceSelect
        label="Тип устройства"
        reference="device-types"
        optionText="name"
        value={(record.typeId as number) ?? null}
        onChange={(v) => onChange('typeId', v)}
        placeholder="Выберите тип"
        onAddNew={() => setCreatingType(true)}
        addNewLabel="Добавить тип"
      />
      {creatingType && (
        <CreateRecordOverlay
          resource="device-types"
          onClose={() => setCreatingType(false)}
          onCreated={(id) => onChange('typeId', id)}
        >
          {(onSuccess) => <DeviceTypeCreate onSuccessWithData={onSuccess} />}
        </CreateRecordOverlay>
      )}
    </div>
  );
}

export const DeviceCatalogEdit = () => (
  <AdminEditForm title="Редактирование устройства">
    {({ record, onChange }) => <DeviceCatalogFormFields record={record} onChange={onChange} />}
  </AdminEditForm>
);

export const DeviceCatalogCreate = ({
  onSuccessWithData,
}: {
  onSuccessWithData?: (data: Record<string, unknown>) => void;
}) => (
  <AdminCreateForm
    title="Новое устройство"
    defaultValues={{ active: false }}
    onSuccessWithData={onSuccessWithData}
  >
    {({ record, onChange }) => <DeviceCatalogFormFields record={record} onChange={onChange} />}
  </AdminCreateForm>
);
