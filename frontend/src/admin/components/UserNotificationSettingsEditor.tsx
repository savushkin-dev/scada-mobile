import { useState } from 'react';
import {
  useRecordContext,
  useGetList,
  useUpdate,
  useCreate,
  useDelete,
  useNotify,
} from 'react-admin';

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

/**
 * Inline-редактор настроек уведомлений внутри карточки сотрудника.
 *
 * Показывает таблицу настроек (автомат + флаги) и позволяет:
 * - переключать "Тех. сбои" / "Вызов" / "Активны";
 * - удалять настройку;
 * - добавлять настройку для ещё не выбранного автомата.
 */
export function UserNotificationSettingsEditor() {
  const record = useRecordContext();
  const userId = record?.id as number | undefined;
  const notify = useNotify();

  const [selectedUnitId, setSelectedUnitId] = useState('');

  const {
    data: settings,
    isLoading: settingsLoading,
    refetch,
  } = useGetList<NotificationSetting>('user-notification-settings', {
    filter: { userId },
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
    return <div className="text-secondary p-2">Загрузка настроек уведомлений...</div>;
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
          notify('Настройка сохранена');
          refetch();
        },
        onError: () => notify('Ошибка сохранения', { type: 'error' }),
      }
    );
  };

  const handleAdd = () => {
    const unitId = Number(selectedUnitId);
    if (!unitId) return;

    create(
      'user-notification-settings',
      {
        data: {
          userId,
          unitId,
          incidentNotificationsEnabled: true,
          androidCallNotificationsEnabled: true,
          active: true,
        },
      },
      {
        onSuccess: () => {
          notify('Настройка добавлена');
          setSelectedUnitId('');
          refetch();
        },
        onError: () => notify('Ошибка добавления', { type: 'error' }),
      }
    );
  };

  const handleDelete = (id: number) => {
    deleteOne(
      'user-notification-settings',
      { id },
      {
        onSuccess: () => {
          notify('Настройка удалена');
          refetch();
        },
        onError: () => notify('Ошибка удаления', { type: 'error' }),
      }
    );
  };

  return (
    <div className="mt-6 rounded border border-gray-200 p-4">
      <h3 className="mb-3 text-lg font-medium">Настройки уведомлений</h3>

      {settingsList.length === 0 ? (
        <p className="text-secondary mb-3">Нет настроек уведомлений</p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead className="border-b text-gray-600">
            <tr>
              <th className="pb-2 font-medium">Автомат</th>
              <th className="pb-2 font-medium">Тех. сбои</th>
              <th className="pb-2 font-medium">Вызов</th>
              <th className="pb-2 font-medium">Активны</th>
              <th className="pb-2 font-medium"></th>
            </tr>
          </thead>
          <tbody>
            {settingsList.map((setting) => (
              <tr key={setting.id} className="border-b last:border-0">
                <td className="py-2 pr-4">{unitName(setting.unitId)}</td>
                <td className="py-2 pr-4">
                  <input
                    type="checkbox"
                    checked={setting.incidentNotificationsEnabled}
                    onChange={() => handleToggle(setting, 'incidentNotificationsEnabled')}
                  />
                </td>
                <td className="py-2 pr-4">
                  <input
                    type="checkbox"
                    checked={setting.androidCallNotificationsEnabled}
                    onChange={() => handleToggle(setting, 'androidCallNotificationsEnabled')}
                  />
                </td>
                <td className="py-2 pr-4">
                  <input
                    type="checkbox"
                    checked={setting.active}
                    onChange={() => handleToggle(setting, 'active')}
                  />
                </td>
                <td className="py-2">
                  <button
                    type="button"
                    className="text-red-600 hover:underline"
                    onClick={() => handleDelete(setting.id)}
                  >
                    Удалить
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {availableUnits.length > 0 && (
        <div className="mt-3 flex items-center gap-2">
          <select
            className="rounded border border-gray-300 px-2 py-1 text-sm"
            value={selectedUnitId}
            onChange={(e) => setSelectedUnitId(e.target.value)}
          >
            <option value="">Выберите автомат...</option>
            {availableUnits.map((unit) => (
              <option key={unit.id} value={unit.id}>
                {unit.name}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="rounded bg-blue-600 px-3 py-1 text-sm text-white disabled:opacity-50"
            disabled={!selectedUnitId}
            onClick={handleAdd}
          >
            Добавить
          </button>
        </div>
      )}
    </div>
  );
}
