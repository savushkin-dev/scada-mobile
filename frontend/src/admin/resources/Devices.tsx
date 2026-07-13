import { useNavigate } from 'react-router-dom';
import { useListContext } from 'react-admin';
import { AdminListContainer } from '../ui/AdminListContainer';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { AdminEditForm } from '../ui/AdminEditForm';
import { AdminCreateForm } from '../ui/AdminCreateForm';
import { RoundedInput } from '../ui/RoundedInput';
import { ReferenceSelect } from '../ui/ReferenceSelect';
import { PillButton } from '../ui/PillButton';
import { IconPencil, IconTrash } from '../ui/icons';
import { useNameMap } from '../ui/useNameMap';

interface Device {
  id: number;
  code: string;
  displayName: string;
  unitId?: number;
  typeId?: number;
}

export const DeviceList = () => {
  const navigate = useNavigate();
  const { data } = useListContext<Device>();
  const records = data ?? [];
  const getUnitName = useNameMap('units');
  const getDeviceTypeName = useNameMap('device-types');

  return (
    <AdminListContainer title="Устройства">
      <MobileCardList
        records={records}
        renderCard={(device) => (
          <div className="rounded-[20px] bg-white p-4">
            <div className="mb-1">
              <span className="text-base font-bold text-[#1a1c1e]">{device.displayName}</span>
            </div>
            <div className="mb-3 text-sm text-[#74777f]">{device.code}</div>
            <div className="flex items-center justify-between gap-2">
              <PillButton
                variant="secondary"
                icon={<IconPencil size={16} />}
                onClick={() => navigate(device.id.toString())}
                className="h-9 px-3 text-xs"
              >
                Изменить
              </PillButton>
              <PillButton
                variant="danger"
                icon={<IconTrash size={16} />}
                onClick={() => navigate(device.id.toString())}
                className="h-9 px-3 text-xs"
              >
                Удалить
              </PillButton>
            </div>
          </div>
        )}
      />
      <DesktopDataTable
        records={records}
        keyExtractor={(device) => device.id}
        columns={[
          { key: 'id', header: 'ID', render: (device) => device.id, className: 'w-12' },
          { key: 'code', header: 'Код', render: (device) => device.code },
          {
            key: 'displayName',
            header: 'Отображаемое имя',
            render: (device) => device.displayName,
          },
          {
            key: 'unit',
            header: 'Автомат',
            render: (device) =>
              device.unitId ? (
                <span className="text-[#4285f4]">{getUnitName(device.unitId)}</span>
              ) : (
                '—'
              ),
          },
          {
            key: 'type',
            header: 'Тип',
            render: (device) =>
              device.typeId ? (
                <span className="text-[#4285f4]">{getDeviceTypeName(device.typeId)}</span>
              ) : (
                '—'
              ),
          },
          {
            key: 'actions',
            header: '',
            render: (device) => (
              <div className="flex items-center justify-end gap-2">
                <PillButton
                  variant="secondary"
                  icon={<IconPencil size={16} />}
                  onClick={() => navigate(device.id.toString())}
                  className="h-9 px-3 text-xs"
                >
                  Изменить
                </PillButton>
                <PillButton
                  variant="danger"
                  icon={<IconTrash size={16} />}
                  onClick={() => navigate(device.id.toString())}
                  className="h-9 px-3 text-xs"
                >
                  Удалить
                </PillButton>
              </div>
            ),
          },
        ]}
      />
    </AdminListContainer>
  );
};

function DeviceFormFields({
  record,
  onChange,
}: {
  record: Record<string, unknown>;
  onChange: (field: string, value: unknown) => void;
}) {
  return (
    <div className="space-y-5">
      <RoundedInput
        label="Код устройства"
        value={(record.code as string) ?? ''}
        onChange={(e) => onChange('code', e.target.value)}
        required
      />
      <RoundedInput
        label="Отображаемое имя"
        value={(record.displayName as string) ?? ''}
        onChange={(e) => onChange('displayName', e.target.value)}
        required
      />
      <ReferenceSelect
        label="Автомат"
        reference="units"
        optionText="name"
        value={(record.unitId as number) ?? null}
        onChange={(v) => onChange('unitId', v)}
        placeholder="Выберите автомат"
      />
      <ReferenceSelect
        label="Тип устройства"
        reference="device-types"
        optionText="name"
        value={(record.typeId as number) ?? null}
        onChange={(v) => onChange('typeId', v)}
        placeholder="Выберите тип"
      />
    </div>
  );
}

export const DeviceEdit = () => (
  <AdminEditForm title="Редактирование устройства">
    {({ record, onChange }) => <DeviceFormFields record={record} onChange={onChange} />}
  </AdminEditForm>
);

export const DeviceCreate = () => (
  <AdminCreateForm title="Новое устройство">
    {({ record, onChange }) => <DeviceFormFields record={record} onChange={onChange} />}
  </AdminCreateForm>
);
