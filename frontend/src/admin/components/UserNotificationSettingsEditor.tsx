import { useState } from 'react';
import {
  useRecordContext,
  useGetList,
  useUpdate,
  useCreate,
  useDelete,
  useNotify,
} from 'react-admin';
import { PillButton } from '../ui/PillButton';
import { SearchableSelect } from '../ui/SearchableSelect';
import { IconPlus, IconCheck, IconChevronDown } from '../ui/icons';

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

interface UserNotificationSettingsEditorProps {
  userId?: number;
}

export function UserNotificationSettingsEditor({ userId }: UserNotificationSettingsEditorProps) {
  const record = useRecordContext();
  const effectiveUserId = userId ?? (record?.id as number | undefined);
  const notify = useNotify();

  const [selectedUnitId, setSelectedUnitId] = useState<string>('');
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

  const settingsList = settings ?? [];
  const unitsList = units ?? [];

  const assignedUnitIds = new Set(settingsList.map((s) => s.unitId));
  const availableUnits = unitsList.filter((u) => !assignedUnitIds.has(u.id));

  const unitName = (unitId: number) =>
    unitsList.find((u) => u.id === unitId)?.name ?? `Автомат ${unitId}`;

  const handleToggle = (setting: NotificationSetting, field: keyof NotificationSetting) => {
    update(
      'user-notification-settings',
      {
        id: setting.id,
        data: { ...setting, [field]: !(setting[field] as boolean) },
      },
      {
        onSuccess: () => {
          notify('Настройка сохранена', { type: 'info' });
          refetch();
        },
        onError: () => notify('Ошибка сохранения', { type: 'error', autoHideDuration: null }),
      }
    );
  };

  const handleAdd = () => {
    const unitId = Number(selectedUnitId);
    if (!unitId || !effectiveUserId) return;

    create(
      'user-notification-settings',
      {
        data: {
          userId: effectiveUserId,
          unitId,
          incidentNotificationsEnabled: true,
          androidCallNotificationsEnabled: true,
          active: true,
        },
      },
      {
        onSuccess: () => {
          notify('Настройка добавлена', { type: 'info' });
          setSelectedUnitId('');
          refetch();
        },
        onError: () => notify('Ошибка добавления', { type: 'error', autoHideDuration: null }),
      }
    );
  };

  const handleDelete = (id: number) => {
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

  return (
    <div>
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center justify-between rounded-[12px] bg-[#f8f9fa] px-4 py-3 text-left transition-colors hover:bg-[#f0f7ff]"
      >
        <span className="text-sm font-semibold text-[#1a1c1e]">
          {expanded ? 'Скрыть настройки' : 'Показать настройки'}
        </span>
        <IconChevronDown
          size={18}
          className={`text-[#74777f] transition-transform duration-300 ${expanded ? 'rotate-180' : ''}`}
        />
      </button>

      <div
        className={`overflow-hidden transition-all duration-300 ease-in-out ${
          expanded ? 'max-h-[2000px] opacity-100' : 'max-h-0 opacity-0'
        }`}
      >
        <div className="overflow-hidden">
          <div className="pt-4">
            {settingsList.length === 0 ? (
              <p className="py-2 text-sm text-[#74777f]">Нет настроек уведомлений</p>
            ) : (
              <>
                {/* Desktop table */}
                <div className="hidden overflow-x-auto lg:block">
                  <table className="w-full border-collapse">
                    <thead>
                      <tr className="border-b border-[#f0f0f0]">
                        <th className="pb-3 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
                          Автомат
                        </th>
                        <th className="pb-3 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
                          Тех. сбои
                        </th>
                        <th className="pb-3 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
                          Вызов
                        </th>
                        <th className="pb-3 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
                          Активны
                        </th>
                        <th className="pb-3" />
                      </tr>
                    </thead>
                    <tbody>
                      {settingsList.map((setting) => (
                        <tr key={setting.id} className="border-b border-[#f0f0f0] last:border-b-0">
                          <td className="py-3 pr-4 text-sm font-medium text-[#1a1c1e]">
                            {unitName(setting.unitId)}
                          </td>
                          <td className="py-3 pr-4">
                            <CheckButton
                              checked={setting.incidentNotificationsEnabled}
                              onChange={() => handleToggle(setting, 'incidentNotificationsEnabled')}
                            />
                          </td>
                          <td className="py-3 pr-4">
                            <CheckButton
                              checked={setting.androidCallNotificationsEnabled}
                              onChange={() =>
                                handleToggle(setting, 'androidCallNotificationsEnabled')
                              }
                            />
                          </td>
                          <td className="py-3 pr-4">
                            <CheckButton
                              checked={setting.active}
                              onChange={() => handleToggle(setting, 'active')}
                            />
                          </td>
                          <td className="py-3 text-right">
                            <button
                              type="button"
                              onClick={() => handleDelete(setting.id)}
                              className="text-sm font-medium text-[#ea4335] hover:underline"
                            >
                              Удалить
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Mobile cards */}
                <div className="flex flex-col gap-3 lg:hidden">
                  {settingsList.map((setting) => (
                    <div key={setting.id} className="rounded-[16px] border border-[#f0f0f0] p-4">
                      <div className="mb-3 flex items-center justify-between">
                        <span className="font-semibold text-[#1a1c1e]">
                          {unitName(setting.unitId)}
                        </span>
                        <button
                          type="button"
                          onClick={() => handleDelete(setting.id)}
                          className="text-sm font-medium text-[#ea4335]"
                        >
                          Удалить
                        </button>
                      </div>
                      <div className="grid grid-cols-3 gap-2">
                        <CheckItem
                          label="Тех. сбои"
                          checked={setting.incidentNotificationsEnabled}
                          onChange={() => handleToggle(setting, 'incidentNotificationsEnabled')}
                        />
                        <CheckItem
                          label="Вызов"
                          checked={setting.androidCallNotificationsEnabled}
                          onChange={() => handleToggle(setting, 'androidCallNotificationsEnabled')}
                        />
                        <CheckItem
                          label="Активны"
                          checked={setting.active}
                          onChange={() => handleToggle(setting, 'active')}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}

            {availableUnits.length > 0 && (
              <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-end">
                <div className="flex-1">
                  <SearchableSelect
                    label="Добавить автомат"
                    options={availableUnits.map((u) => ({ id: u.id, label: u.name }))}
                    value={selectedUnitId ? Number(selectedUnitId) : null}
                    onChange={(v) => setSelectedUnitId(v != null ? String(v) : '')}
                    placeholder="Выберите автомат"
                  />
                </div>
                <PillButton
                  icon={<IconPlus size={16} />}
                  onClick={handleAdd}
                  disabled={!selectedUnitId}
                  className="h-10"
                >
                  Добавить
                </PillButton>
              </div>
            )}
          </div>
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
      className={`flex h-8 w-8 items-center justify-center rounded-[10px] border transition-colors ${
        checked
          ? 'border-[#4285f4] bg-[#4285f4] text-white'
          : 'border-[#e8eaed] bg-white text-transparent hover:border-[#c4c7cc]'
      }`}
    >
      <IconCheck size={18} />
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
      className={`flex flex-col items-center gap-1.5 rounded-[12px] py-2 transition-colors ${
        checked ? 'bg-[#f0f7ff] text-[#4285f4]' : 'bg-[#f8f9fa] text-[#74777f]'
      }`}
    >
      <span className="text-[11px] font-medium uppercase tracking-wide">{label}</span>
      <div
        className={`flex h-6 w-6 items-center justify-center rounded-[8px] border ${
          checked
            ? 'border-[#4285f4] bg-[#4285f4] text-white'
            : 'border-[#e8eaed] bg-white text-transparent'
        }`}
      >
        <IconCheck size={14} />
      </div>
    </button>
  );
}
