import { useState } from 'react';
import { useListContext } from 'react-admin';
import { AdminListContainer } from '../ui/AdminListContainer';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { AdminEditForm } from '../ui/AdminEditForm';
import { AdminCreateForm } from '../ui/AdminCreateForm';
import { CreateRecordOverlay } from '../ui/CreateRecordOverlay';
import { WorkshopCreate } from './Workshops';
import { DeviceCatalogCreate } from './DeviceCatalog';
import { RoundedInput } from '../ui/RoundedInput';
import { AdminChip } from '../ui/AdminChip';
import { ReferenceSelect } from '../ui/ReferenceSelect';
import { formatEmpty } from '../ui/formatEmpty';
import { useNameMap } from '../ui/useNameMap';
import { IconUnits } from '../ui/icons';
import { RowActionsMenu } from '../ui/RowActionsMenu';
import { useRowActions } from '../ui/useRowActions';
import { UNIT_FILTER_FIELDS } from '../filters/configs';

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
  const { navigateToEdit, toggleActive, deleteRecord } = useRowActions();
  const { data } = useListContext<Unit>();
  const records = data ?? [];

  return (
    <AdminListContainer title="Автоматы" records={records} filterFields={UNIT_FILTER_FIELDS}>
      {({ records: filtered }) => (
        <>
          <MobileCardList
            records={filtered}
            renderCard={(unit) => (
              <div className={`rounded-[20px] p-4 ${unit.active ? 'bg-white' : 'bg-[#f8f9fa]'}`}>
                <div className={unit.active ? '' : 'opacity-60'}>
                  <div className="mb-1">
                    <span className="text-base font-bold text-[#1a1c1e]">
                      {formatEmpty(unit.name)}
                    </span>
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
                </div>
                <div className="flex items-center justify-end">
                  <RowActionsMenu
                    onEdit={() => navigateToEdit(unit.id)}
                    isActive={unit.active}
                    onToggleActive={() => toggleActive(unit)}
                    onDelete={() => deleteRecord(unit)}
                  />
                </div>
              </div>
            )}
          />
          <DesktopDataTable
            records={filtered}
            keyExtractor={(unit) => unit.id}
            isActive={(unit) => unit.active}
            columns={[
              {
                key: 'id',
                header: 'ID',
                render: (unit) => unit.id,
                className: 'w-12',
                filterKey: 'id',
              },
              { key: 'name', header: 'Название', render: (unit) => unit.name, filterKey: 'name' },
              {
                key: 'workshop',
                header: 'Цех',
                filterKey: 'workshopId',
                render: (unit) => <WorkshopName id={unit.workshopId} />,
              },
              {
                key: 'printsrv',
                header: 'PrintSrv ID',
                filterKey: 'printsrvInstanceId',
                render: (unit) => unit.printsrvInstanceId,
              },
              {
                key: 'host',
                header: 'Хост',
                render: (unit) => unit.printsrvHost,
                filterKey: 'printsrvHost',
              },
              {
                key: 'port',
                header: 'Порт',
                filterKey: 'printsrvPort',
                render: (unit) => unit.printsrvPort,
                className: 'w-16',
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
                  <div className="flex items-center justify-end">
                    <RowActionsMenu
                      onEdit={() => navigateToEdit(unit.id)}
                      isActive={unit.active}
                      onToggleActive={() => toggleActive(unit)}
                      onDelete={() => deleteRecord(unit)}
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

function WorkshopName({ id }: { id: number }) {
  const getName = useNameMap('workshops');
  return <span className="text-[#1a1c1e]">{formatEmpty(getName(id))}</span>;
}

function UnitLeftFields({
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
    </div>
  );
}

function UnitRightFields({
  record,
  onChange,
}: {
  record: Record<string, unknown>;
  onChange: (field: string, value: unknown) => void;
}) {
  const [creatingWorkshop, setCreatingWorkshop] = useState(false);
  const [creatingDevice, setCreatingDevice] = useState(false);

  return (
    <div className="space-y-5">
      <ReferenceSelect
        label="Цех"
        reference="workshops"
        optionText="name"
        value={(record.workshopId as number) ?? null}
        onChange={(v) => onChange('workshopId', v)}
        placeholder="Выберите цех"
        onAddNew={() => setCreatingWorkshop(true)}
        addNewLabel="Добавить цех"
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
      <ReferenceSelect
        label="Устройства"
        reference="device-catalog"
        optionText="name"
        multiple
        value={(record.catalogIds as number[]) ?? []}
        onChange={(v) => onChange('catalogIds', v ?? [])}
        placeholder="Выберите устройства"
        onAddNew={() => setCreatingDevice(true)}
        addNewLabel="Добавить устройство"
      />
      {creatingWorkshop && (
        <CreateRecordOverlay
          resource="workshops"
          onClose={() => setCreatingWorkshop(false)}
          onCreated={(id) => onChange('workshopId', id)}
        >
          {(onSuccess) => <WorkshopCreate onSuccessWithData={onSuccess} />}
        </CreateRecordOverlay>
      )}
      {creatingDevice && (
        <CreateRecordOverlay
          resource="device-catalog"
          onClose={() => setCreatingDevice(false)}
          onCreated={(id) =>
            onChange('catalogIds', [...((record.catalogIds as number[]) ?? []), id])
          }
        >
          {(onSuccess) => <DeviceCatalogCreate onSuccessWithData={onSuccess} />}
        </CreateRecordOverlay>
      )}
    </div>
  );
}

export const UnitEdit = () => (
  <AdminEditForm
    title="Редактирование автомата"
    layout="two-column"
    defaultLeftWidth={25}
    leftCardTitle="Основная информация"
    rightCardTitle="Подключение и устройства"
    rightCardIcon={<IconUnits size={20} />}
  >
    {({ record, onChange, slot }) =>
      slot === 'left' ? (
        <UnitLeftFields record={record} onChange={onChange} />
      ) : (
        <UnitRightFields record={record} onChange={onChange} />
      )
    }
  </AdminEditForm>
);

export const UnitCreate = ({
  onSuccessWithData,
}: {
  onSuccessWithData?: (data: Record<string, unknown>) => void;
}) => (
  <AdminCreateForm
    title="Новый автомат"
    layout="two-column"
    defaultValues={{ active: true, catalogIds: [] }}
    onSuccessWithData={onSuccessWithData}
    leftCardTitle="Основная информация"
    rightCardTitle="Подключение и устройства"
    rightCardIcon={<IconUnits size={20} />}
  >
    {({ record, onChange, slot }) =>
      slot === 'left' ? (
        <UnitLeftFields record={record} onChange={onChange} />
      ) : (
        <UnitRightFields record={record} onChange={onChange} />
      )
    }
  </AdminCreateForm>
);
