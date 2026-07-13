import { useNavigate } from 'react-router-dom';
import { useListContext } from 'react-admin';
import { AdminListContainer } from '../ui/AdminListContainer';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { AdminEditForm } from '../ui/AdminEditForm';
import { AdminCreateForm } from '../ui/AdminCreateForm';
import { RoundedInput } from '../ui/RoundedInput';
import { IOSSwitch } from '../ui/IOSSwitch';
import { StatusPill } from '../ui/StatusPill';
import { ReferenceSelect } from '../ui/ReferenceSelect';
import { PillButton } from '../ui/PillButton';
import { AdminDeleteButton } from '../ui/AdminDeleteButton';
import { formatEmpty } from '../ui/formatEmpty';
import { IconPencil } from '../ui/icons';
import { useNameMap } from '../ui/useNameMap';

interface DeviceCatalogItem {
  id: number;
  code: string;
  name: string;
  typeId?: number | null;
  active: boolean;
}

export const DeviceCatalogList = () => {
  const navigate = useNavigate();
  const { data } = useListContext<DeviceCatalogItem>();
  const records = data ?? [];
  const getDeviceTypeName = useNameMap('device-types');

  return (
    <AdminListContainer title="Справочник устройств">
      <MobileCardList
        records={records}
        renderCard={(item) => (
          <div className="rounded-[20px] bg-white p-4">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-base font-bold text-[#1a1c1e]">{formatEmpty(item.name)}</span>
              <StatusPill variant={item.active ? 'active' : 'inactive'}>
                {item.active ? 'Активно' : 'Неактивно'}
              </StatusPill>
            </div>
            <div className="mb-3 text-sm text-[#74777f]">{formatEmpty(item.code)}</div>
            <div className="mb-3 text-sm text-[#74777f]">
              {formatEmpty(item.typeId ? getDeviceTypeName(item.typeId) : null)}
            </div>
            <div className="flex items-center justify-between gap-2">
              <PillButton
                variant="secondary"
                icon={<IconPencil size={16} />}
                onClick={() => navigate(item.id.toString())}
                className="h-9 px-3 text-xs"
              >
                Изменить
              </PillButton>
              <AdminDeleteButton record={item} size="small" />
            </div>
          </div>
        )}
      />
      <DesktopDataTable
        records={records}
        keyExtractor={(item) => item.id}
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
            key: 'state',
            header: 'Состояние',
            render: (item) => (
              <StatusPill
                variant={item.active ? 'active' : item.typeId == null ? 'warning' : 'inactive'}
              >
                {item.active ? 'Активно' : item.typeId == null ? 'Требует настройки' : 'Неактивно'}
              </StatusPill>
            ),
          },
          {
            key: 'actions',
            header: '',
            render: (item) => (
              <div className="flex items-center justify-end gap-2">
                <PillButton
                  variant="secondary"
                  icon={<IconPencil size={16} />}
                  onClick={() => navigate(item.id.toString())}
                  className="h-9 px-3 text-xs"
                >
                  Изменить
                </PillButton>
                <AdminDeleteButton record={item} size="small" />
              </div>
            ),
          },
        ]}
      />
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
      />
      <label className="flex items-center justify-between">
        <span className="text-sm font-medium text-[#1a1c1e]">Активно</span>
        <IOSSwitch
          checked={!!record.active}
          onChange={(e) => onChange('active', e.target.checked)}
        />
      </label>
    </div>
  );
}

export const DeviceCatalogEdit = () => (
  <AdminEditForm title="Редактирование устройства">
    {({ record, onChange }) => <DeviceCatalogFormFields record={record} onChange={onChange} />}
  </AdminEditForm>
);

export const DeviceCatalogCreate = () => (
  <AdminCreateForm title="Новое устройство" defaultValues={{ active: false }}>
    {({ record, onChange }) => <DeviceCatalogFormFields record={record} onChange={onChange} />}
  </AdminCreateForm>
);
