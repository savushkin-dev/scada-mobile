import { useMemo, useState } from 'react';
import {
  useRecordContext,
  useGetList,
  useUpdate,
  useCreate,
  useDelete,
  useNotify,
} from 'react-admin';
import { RowActionsMenu } from '../ui/RowActionsMenu';
import { IconCheck, IconChevronDown } from '../ui/icons';

interface NotificationSetting {
  id: number;
  userId: number;
  unitId: number;
  incidentNotificationsEnabled: boolean;
  androidCallNotificationsEnabled: boolean;
  active: boolean;
  updatedAt: string;
}

interface Unit {
  id: number;
  name: string;
}

interface RowViewModel {
  unitId: number;
  unitName: string;
  setting?: NotificationSetting;
  incidentNotificationsEnabled: boolean;
  androidCallNotificationsEnabled: boolean;
  active: boolean;
}

interface UserNotificationSettingsEditorProps {
  userId?: number;
}

const DEFAULT_FLAGS = {
  incidentNotificationsEnabled: true,
  androidCallNotificationsEnabled: true,
  active: true,
};

export function UserNotificationSettingsEditor({ userId }: UserNotificationSettingsEditorProps) {
  const record = useRecordContext();
  const effectiveUserId = userId ?? (record?.id as number | undefined);
  const notify = useNotify();

  const [expanded, setExpanded] = useState(false);

  const {
    data: settings,
    isLoading: settingsLoading,
    refetch,
  } = useGetList<NotificationSetting>('user-notification-settings', {
    filter: { userId: effectiveUserId },
    pagination: { page: 1, perPage: 1000 },
    sort: { field: 'id', order: 'ASC' },
  });

  const { data: units, isLoading: unitsLoading } = useGetList<Unit>('units', {
    pagination: { page: 1, perPage: 1000 },
    sort: { field: 'name', order: 'ASC' },
  });

  const [update] = useUpdate();
  const [create] = useCreate();
  const [deleteOne] = useDelete();

  const rows = useMemo<RowViewModel[]>(() => {
    const settingsList = settings ?? [];
    const unitsList = units ?? [];
    const byUnitId = new Map(settingsList.map((s) => [s.unitId, s]));

    return unitsList.map((unit) => {
      const setting = byUnitId.get(unit.id);
      return {
        unitId: unit.id,
        unitName: unit.name,
        setting,
        incidentNotificationsEnabled:
          setting?.incidentNotificationsEnabled ?? DEFAULT_FLAGS.incidentNotificationsEnabled,
        androidCallNotificationsEnabled:
          setting?.androidCallNotificationsEnabled ?? DEFAULT_FLAGS.androidCallNotificationsEnabled,
        active: setting?.active ?? DEFAULT_FLAGS.active,
      };
    });
  }, [settings, units]);

  const allSelected = useMemo(
    () =>
      rows.length > 0 &&
      rows.every((row) => row.incidentNotificationsEnabled && row.androidCallNotificationsEnabled),
    [rows]
  );

  const upsert = (row: RowViewModel, data: Partial<NotificationSetting>) => {
    if (!effectiveUserId) return;

    if (row.setting) {
      update(
        'user-notification-settings',
        {
          id: row.setting.id,
          data: { ...row.setting, ...data },
        },
        {
          onSuccess: () => refetch(),
          onError: () => notify('Ошибка сохранения', { type: 'error', autoHideDuration: null }),
        }
      );
    } else {
      create(
        'user-notification-settings',
        {
          data: {
            userId: effectiveUserId,
            unitId: row.unitId,
            incidentNotificationsEnabled:
              data.incidentNotificationsEnabled ?? DEFAULT_FLAGS.incidentNotificationsEnabled,
            androidCallNotificationsEnabled:
              data.androidCallNotificationsEnabled ?? DEFAULT_FLAGS.androidCallNotificationsEnabled,
            active: data.active ?? DEFAULT_FLAGS.active,
          },
        },
        {
          onSuccess: () => refetch(),
          onError: () => notify('Ошибка сохранения', { type: 'error', autoHideDuration: null }),
        }
      );
    }
  };

  const handleToggleField = (
    row: RowViewModel,
    field: 'incidentNotificationsEnabled' | 'androidCallNotificationsEnabled'
  ) => {
    upsert(row, { [field]: !row[field] });
  };

  const handleToggleActive = (row: RowViewModel) => {
    upsert(row, { active: !row.active });
  };

  const handleDelete = (id: number | undefined) => {
    if (!id) return;
    deleteOne(
      'user-notification-settings',
      { id },
      {
        onSuccess: () => {
          notify('Настройка удалена', { type: 'info' });
          refetch();
        },
        onError: () => notify('Ошибка удаления', { type: 'error', autoHideDuration: null }),
      }
    );
  };

  const handleToggleAll = () => {
    const nextValue = !allSelected;
    rows.forEach((row) => {
      upsert(row, {
        incidentNotificationsEnabled: nextValue,
        androidCallNotificationsEnabled: nextValue,
      });
    });
  };

  if (settingsLoading || unitsLoading) {
    return (
      <div className="rounded-[16px] border border-[#e8eaed] bg-white p-5 lg:rounded-[20px] lg:p-6">
        <div className="pb-3 text-base font-bold text-[#1a1c1e] lg:text-lg">
          Настройки уведомлений
        </div>
        <div className="py-4 text-sm text-[#74777f]">Загрузка настроек уведомлений...</div>
      </div>
    );
  }

  return (
    <div className="m-1 flex flex-1 flex-col min-h-0 lg:m-2">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full shrink-0 items-center justify-between rounded-[12px] bg-[#f8f9fa] px-4 py-3 text-left transition-colors hover:bg-[#f0f7ff]"
      >
        <span className="text-sm font-semibold text-[#1a1c1e]">Настройки уведомлений</span>
        <IconChevronDown
          size={18}
          className={`text-[#74777f] transition-transform duration-300 ${expanded ? 'rotate-180' : ''}`}
        />
      </button>

      <div
        className={`overflow-hidden transition-all duration-300 ease-in-out ${
          expanded ? 'max-h-[2000px] flex-1 opacity-100' : 'max-h-0 opacity-0'
        }`}
      >
        <div className="flex h-full min-h-0 flex-col pt-3">
          {rows.length === 0 ? (
            <p className="py-2 text-sm text-[#74777f]">Нет доступных автоматов</p>
          ) : (
            <>
              <div className="mb-2 flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleToggleAll}
                  aria-pressed={allSelected}
                  className={`flex h-3 w-3 items-center justify-center rounded-[4px] border transition-colors ${
                    allSelected
                      ? 'border-[#4285f4] bg-[#4285f4] text-white'
                      : 'border-[#e8eaed] bg-white text-transparent hover:border-[#c4c7cc]'
                  }`}
                >
                  <IconCheck size={8} />
                </button>
                <span className="text-sm font-medium text-[#1a1c1e]">Выбрать всё</span>
              </div>

              <div className="flex min-h-0 flex-1 flex-col rounded-[12px] border border-[#e8eaed] p-3">
                <div className="hidden min-h-0 flex-1 overflow-y-auto lg:block">
                  <table className="w-full border-collapse">
                    <thead className="sticky top-0 z-10 bg-white">
                      <tr className="border-b border-[#f0f0f0]">
                        <th className="pb-2 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
                          Автомат
                        </th>
                        <th className="pb-2 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
                          Тех. сбои
                        </th>
                        <th className="pb-2 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
                          Вызов
                        </th>
                        <th className="pb-2" />
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((row) => {
                        const settingId = row.setting?.id;
                        return (
                          <tr
                            key={row.unitId}
                            className="border-b border-[#f0f0f0] last:border-b-0 even:bg-[#edf0f4]"
                          >
                            <td className="py-1.5 pr-4 text-sm font-medium text-[#1a1c1e]">
                              {row.unitName}
                            </td>
                            <td className="py-1.5 pr-4">
                              <CheckButton
                                checked={row.incidentNotificationsEnabled}
                                onChange={() =>
                                  handleToggleField(row, 'incidentNotificationsEnabled')
                                }
                              />
                            </td>
                            <td className="py-1.5 pr-4">
                              <CheckButton
                                checked={row.androidCallNotificationsEnabled}
                                onChange={() =>
                                  handleToggleField(row, 'androidCallNotificationsEnabled')
                                }
                              />
                            </td>
                            <td className="py-1.5 text-right">
                              <RowActionsMenu
                                isActive={row.active}
                                onToggleActive={() => handleToggleActive(row)}
                                onDelete={
                                  settingId != null ? () => handleDelete(settingId) : undefined
                                }
                              />
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                <div className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto lg:hidden">
                  {rows.map((row) => {
                    const settingId = row.setting?.id;
                    return (
                      <div
                        key={row.unitId}
                        className="rounded-[16px] border border-[#f0f0f0] p-2.5"
                      >
                        <div className="mb-2 flex items-center justify-between">
                          <span className="font-semibold text-[#1a1c1e]">{row.unitName}</span>
                          <RowActionsMenu
                            isActive={row.active}
                            onToggleActive={() => handleToggleActive(row)}
                            onDelete={settingId != null ? () => handleDelete(settingId) : undefined}
                          />
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                          <CheckItem
                            label="Тех. сбои"
                            checked={row.incidentNotificationsEnabled}
                            onChange={() => handleToggleField(row, 'incidentNotificationsEnabled')}
                          />
                          <CheckItem
                            label="Вызов"
                            checked={row.androidCallNotificationsEnabled}
                            onChange={() =>
                              handleToggleField(row, 'androidCallNotificationsEnabled')
                            }
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function CheckButton({ checked, onChange }: { checked: boolean; onChange: () => void }) {
  return (
    <button
      type="button"
      onClick={onChange}
      aria-pressed={checked}
      className={`flex h-4 w-4 items-center justify-center rounded-[5px] border transition-colors ${
        checked
          ? 'border-[#4285f4] bg-[#4285f4] text-white'
          : 'border-[#e8eaed] bg-white text-transparent hover:border-[#c4c7cc]'
      }`}
    >
      <IconCheck size={10} />
    </button>
  );
}

function CheckItem({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onChange}
      aria-pressed={checked}
      className={`flex flex-col items-center gap-1.5 rounded-[12px] py-1.5 transition-colors ${
        checked ? 'bg-[#f0f7ff] text-[#4285f4]' : 'bg-[#f8f9fa] text-[#74777f]'
      }`}
    >
      <span className="text-[11px] font-medium uppercase tracking-wide">{label}</span>
      <div
        className={`flex h-3 w-3 items-center justify-center rounded-[4px] border ${
          checked
            ? 'border-[#4285f4] bg-[#4285f4] text-white'
            : 'border-[#e8eaed] bg-white text-transparent'
        }`}
      >
        <IconCheck size={8} />
      </div>
    </button>
  );
}
