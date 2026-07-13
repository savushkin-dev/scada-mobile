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
import { AdminChip } from '../ui/AdminChip';
import { ReferenceSelect } from '../ui/ReferenceSelect';
import { PillButton } from '../ui/PillButton';
import { AdminDeleteButton } from '../ui/AdminDeleteButton';
import { formatEmpty } from '../ui/formatEmpty';
import { useNameMap } from '../ui/useNameMap';
import { IconPencil } from '../ui/icons';

interface Unit {
  id: number;
  name: string;
  workshopId: number;
  printsrvInstanceId: string;
  printsrvHost: string;
  printsrvPort: number;
  active: boolean;
  deviceNames?: string[];
  catalogIds?: number[];
}

export const UnitList = () => {
  const navigate = useNavigate();
  const { data } = useListContext<Unit>();
  const records = data ?? [];

  return (
    <AdminListContainer title="Автоматы">
      <MobileCardList
        records={records}
        renderCard={(unit) => (
          <div className="rounded-[20px] bg-white p-4">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-base font-bold text-[#1a1c1e]">{formatEmpty(unit.name)}</span>
              <StatusPill variant={unit.active ? 'active' : 'inactive'}>
                {unit.active ? 'Активен' : 'Неактивен'}
              </StatusPill>
            </div>
            <div className="mb-3 space-y-1 text-sm">
              <div className="flex justify-between">
                <span className="text-[#74777f]">PrintSrv ID</span>
                <span className="text-[#1a1c1e]">{formatEmpty(unit.printsrvInstanceId)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#74777f]">Хост</span>
                <span className="text-[#1a1c1e]">{formatEmpty(unit.printsrvHost)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#74777f]">Порт</span>
                <span className="text-[#1a1c1e]">{formatEmpty(unit.printsrvPort)}</span>
              </div>
            </div>
            {unit.deviceNames && unit.deviceNames.length > 0 && (
              <div className="mb-3 flex flex-wrap gap-1">
                {unit.deviceNames.slice(0, 3).map((name) => (
                  <AdminChip key={name}>{name}</AdminChip>
                ))}
                {unit.deviceNames.length > 3 && (
                  <AdminChip>+{unit.deviceNames.length - 3}</AdminChip>
                )}
              </div>
            )}
            <div className="flex items-center justify-between gap-2">
              <PillButton
                variant="secondary"
                icon={<IconPencil size={16} />}
                onClick={() => navigate(unit.id.toString())}
                className="h-9 px-3 text-xs"
              >
                Изменить
              </PillButton>
              <AdminDeleteButton record={unit} size="small" />
            </div>
          </div>
        )}
      />
      <DesktopDataTable
        records={records}
        keyExtractor={(unit) => unit.id}
        columns={[
          { key: 'id', header: 'ID', render: (unit) => unit.id, className: 'w-12' },
          { key: 'name', header: 'Название', render: (unit) => unit.name },
          {
            key: 'workshop',
            header: 'Цех',
            render: (unit) => <WorkshopName id={unit.workshopId} />,
          },
          {
            key: 'printsrv',
            header: 'PrintSrv ID',
            render: (unit) => unit.printsrvInstanceId,
          },
          { key: 'host', header: 'Хост', render: (unit) => unit.printsrvHost },
          { key: 'port', header: 'Порт', render: (unit) => unit.printsrvPort, className: 'w-16' },
          {
            key: 'active',
            header: 'Активен',
            render: (unit) => (
              <StatusPill variant={unit.active ? 'active' : 'inactive'}>
                {unit.active ? 'Активен' : 'Неактивен'}
              </StatusPill>
            ),
          },
          {
            key: 'devices',
            header: 'Устройства',
            render: (unit) => (
              <div className="flex flex-wrap gap-1">
                {unit.deviceNames?.slice(0, 2).map((name) => (
                  <AdminChip key={name}>{name}</AdminChip>
                ))}
                {(unit.deviceNames?.length ?? 0) > 2 && (
                  <AdminChip>+{(unit.deviceNames?.length ?? 0) - 2}</AdminChip>
                )}
              </div>
            ),
          },
          {
            key: 'actions',
            header: '',
            render: (unit) => (
              <div className="flex items-center justify-end gap-2">
                <PillButton
                  variant="secondary"
                  icon={<IconPencil size={16} />}
                  onClick={() => navigate(unit.id.toString())}
                  className="h-9 px-3 text-xs"
                >
                  Изменить
                </PillButton>
                <AdminDeleteButton record={unit} size="small" />
              </div>
            ),
          },
        ]}
      />
    </AdminListContainer>
  );
};

function WorkshopName({ id }: { id: number }) {
  const getName = useNameMap('workshops');
  return <span className="text-[#1a1c1e]">{formatEmpty(getName(id))}</span>;
}

function UnitFormFields({
  record,
  onChange,
}: {
  record: Record<string, unknown>;
  onChange: (field: string, value: unknown) => void;
}) {
  return (
    <div className="space-y-5">
      <RoundedInput
        label="Название автомата"
        value={(record.name as string) ?? ''}
        onChange={(e) => onChange('name', e.target.value)}
        required
      />
      <ReferenceSelect
        label="Цех"
        reference="workshops"
        optionText="name"
        value={(record.workshopId as number) ?? null}
        onChange={(v) => onChange('workshopId', v)}
        placeholder="Выберите цех"
      />
      <RoundedInput
        label="PrintSrv ID"
        value={(record.printsrvInstanceId as string) ?? ''}
        onChange={(e) => onChange('printsrvInstanceId', e.target.value)}
        required
      />
      <RoundedInput
        label="Хост"
        value={(record.printsrvHost as string) ?? ''}
        onChange={(e) => onChange('printsrvHost', e.target.value)}
        required
      />
      <RoundedInput
        label="Порт"
        type="number"
        value={(record.printsrvPort as number) ?? ''}
        onChange={(e) => onChange('printsrvPort', Number(e.target.value))}
        required
      />
      <label className="flex items-center justify-between">
        <span className="text-sm font-medium text-[#1a1c1e]">Активен</span>
        <IOSSwitch
          checked={!!record.active}
          onChange={(e) => onChange('active', e.target.checked)}
        />
      </label>
      <ReferenceSelect
        label="Устройства"
        reference="device-catalog"
        optionText="name"
        multiple
        value={(record.catalogIds as number[]) ?? []}
        onChange={(v) => onChange('catalogIds', v ?? [])}
        placeholder="Выберите устройства"
      />
    </div>
  );
}

export const UnitEdit = () => (
  <AdminEditForm title="Редактирование автомата">
    {({ record, onChange }) => <UnitFormFields record={record} onChange={onChange} />}
  </AdminEditForm>
);

export const UnitCreate = () => (
  <AdminCreateForm title="Новый автомат" defaultValues={{ active: true, catalogIds: [] }}>
    {({ record, onChange }) => <UnitFormFields record={record} onChange={onChange} />}
  </AdminCreateForm>
);
