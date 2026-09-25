import { useEffect, useState } from 'react';
import { useGetList, useListContext } from 'react-admin';
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
import { IOSSwitch } from '../ui/IOSSwitch';
import { formatEmpty } from '../ui/formatEmpty';
import { useNameMap } from '../ui/useNameMap';
import { IconUnits } from '../ui/icons';
import { RowActionsMenu } from '../ui/RowActionsMenu';
import { useRowActions } from '../ui/useRowActions';
import { UNIT_FILTER_FIELDS } from '../filters/configs';
import { fetchDevicesTopology } from '../../api/workshops';

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
  deviceLinks?: DeviceLink[];
}

/** Per-unit раскладка устройства (связь автомат ↔ справочник). */
interface DeviceLink {
  /** ID связи на backend; отсутствует у только что добавленных устройств. */
  id?: number;
  catalogId: number;
  displayName: string;
  groupLabel: string;
  displayOrder: number;
  showCounters: boolean;
  scadaPrefix: string;
  hidden: boolean;
}

export const UnitList = () => {
  const { navigateToEdit, toggleActive, deleteRecord } = useRowActions();
  const { data } = useListContext<Unit>();
  const records = [...(data ?? [])].sort((left, right) => {
    if (left.active !== right.active) return left.active ? -1 : 1;
    return left.name.localeCompare(right.name, 'ru', { sensitivity: 'base' });
  });

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
                filterKey: 'deviceCatalogId',
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

/**
 * Редактор per-unit раскладки устройств: имя на экране, группа, порядок,
 * счётчики, SCADA-префикс. Данные — record.deviceLinks (загружаются в
 * dataProvider.getOne('units') из GET /admin/devices?unitId=), сохранение —
 * PUT /admin/devices/{id} в dataProvider.update('units').
 * Пустые строки трактуются backend как NULL (значения по умолчанию).
 */
function UnitDeviceLayoutEditor({
  record,
  onChange,
}: {
  record: Record<string, unknown>;
  onChange: (field: string, value: unknown) => void;
}) {
  const catalogIds = (record.catalogIds as number[]) ?? [];
  const links = (record.deviceLinks as DeviceLink[]) ?? [];
  const { data: catalog } = useGetList('device-catalog', {
    pagination: { page: 1, perPage: 1000 },
    sort: { field: 'id', order: 'ASC' },
  });

  // Разрешённые дефолтные группы (code → label) из публичной топологии —
  // для плейсхолдера «Группа». Один fetch на открытие формы, без поллинга.
  const workshopId = Number(record.workshopId);
  const printsrvInstanceId = (record.printsrvInstanceId as string) ?? '';
  const [defaultGroupByCode, setDefaultGroupByCode] = useState<Map<string, string> | null>(null);
  useEffect(() => {
    if (!workshopId || !printsrvInstanceId) return;
    let cancelled = false;
    fetchDevicesTopology(workshopId, printsrvInstanceId)
      .then(({ data }) => {
        if (cancelled || !data) return;
        const map = new Map<string, string>();
        for (const group of data.groups) {
          for (const code of group.codes) {
            if (!map.has(code)) map.set(code, group.label);
          }
        }
        setDefaultGroupByCode(map);
      })
      .catch(() => {
        // Топология недоступна — остаётся старый текст «по умолчанию».
      });
    return () => {
      cancelled = true;
    };
  }, [workshopId, printsrvInstanceId]);

  if (catalogIds.length === 0) return null;

  const catalogById = new Map<number, Record<string, unknown>>();
  for (const item of catalog ?? []) catalogById.set(Number(item.id), item);

  const updateLink = (catalogId: number, patch: Partial<DeviceLink>) => {
    const existing = links.find((l) => l.catalogId === catalogId);
    const base: DeviceLink = existing ?? {
      catalogId,
      displayName: '',
      groupLabel: '',
      displayOrder: 0,
      showCounters: false,
      scadaPrefix: '',
      hidden: false,
    };
    const rest = links.filter((l) => l.catalogId !== catalogId);
    onChange('deviceLinks', [...rest, { ...base, ...patch }]);
  };

  return (
    <div>
      <div className="mb-1.5 text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
        Раскладка устройств
      </div>
      <div className="space-y-3">
        {catalogIds.map((catalogId) => {
          const link = links.find((l) => l.catalogId === catalogId);
          const item = catalogById.get(catalogId);
          const catalogName = (item?.name as string) ?? String(catalogId);
          const catalogCode = (item?.code as string) ?? '';
          const deviceCode = catalogCode || String(catalogId);
          const hidden = link?.hidden === true;
          // Дефолтная группа устройства из публичной топологии (разрешённые
          // backend'ом значения). Если кода там нет — старый текст «по умолчанию».
          const defaultGroupLabel = defaultGroupByCode?.get(deviceCode);
          return (
            <div
              key={catalogId}
              className={`rounded-[14px] border-[1.5px] border-[#e8eaed] bg-white p-3 ${hidden ? 'opacity-60' : ''}`}
            >
              <div className="mb-2 text-sm font-bold text-[#1a1c1e]">
                {catalogCode ? `${catalogCode} — ${catalogName}` : catalogName}
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <RoundedInput
                  label="Имя на экране"
                  value={link?.displayName ?? ''}
                  placeholder={catalogName}
                  onChange={(e) => updateLink(catalogId, { displayName: e.target.value })}
                />
                <RoundedInput
                  label="Группа"
                  value={link?.groupLabel ?? ''}
                  placeholder={
                    defaultGroupLabel ? `по умолчанию: ${defaultGroupLabel}` : 'по умолчанию'
                  }
                  onChange={(e) => updateLink(catalogId, { groupLabel: e.target.value })}
                />
                <RoundedInput
                  label="Порядок"
                  type="number"
                  value={link?.displayOrder ?? 0}
                  onChange={(e) =>
                    updateLink(catalogId, { displayOrder: Number(e.target.value) || 0 })
                  }
                />
                <RoundedInput
                  label="SCADA-префикс"
                  value={link?.scadaPrefix ?? ''}
                  placeholder="авто"
                  onChange={(e) => updateLink(catalogId, { scadaPrefix: e.target.value })}
                />
              </div>
              <div className="mt-3 flex items-center gap-4">
                <div className="flex items-center gap-2">
                  <IOSSwitch
                    scale="compact"
                    checked={link?.showCounters === true}
                    onChange={(e) => updateLink(catalogId, { showCounters: e.target.checked })}
                  />
                  <span className="text-sm text-[#1a1c1e]">Счётчики</span>
                </div>
                <div className="flex items-center gap-2">
                  <IOSSwitch
                    scale="compact"
                    checked={hidden}
                    onChange={(e) => updateLink(catalogId, { hidden: e.target.checked })}
                  />
                  <span className="text-sm text-[#1a1c1e]">Скрыт</span>
                </div>
              </div>
              {hidden && (
                <p className="mt-1.5 text-xs text-[#74777f]">
                  Не показывается на вкладке устройств; удалённое устройство вернётся
                  авто-обнаружением.
                </p>
              )}
            </div>
          );
        })}
      </div>
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
      {/* Раскладка устройств — только в режиме редактирования: в форме создания
          автомата связей устройств ещё нет (создаются backend'ом при сохранении). */}
      {record.id != null && <UnitDeviceLayoutEditor record={record} onChange={onChange} />}
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
