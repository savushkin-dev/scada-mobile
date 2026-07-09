import { AdminEditForm } from '../ui/AdminEditForm';
import { ReferenceSelect } from '../ui/ReferenceSelect';
import { IOSSwitch } from '../ui/IOSSwitch';

export const NotificationSettingsEdit = () => (
  <AdminEditForm title="Редактирование настройки уведомлений">
    {({ record, onChange }) => (
      <div className="space-y-5">
        <ReferenceSelect
          label="Сотрудник"
          reference="users"
          optionText="fullName"
          value={(record.userId as number) ?? null}
          onChange={(v) => onChange('userId', v)}
          placeholder="Выберите сотрудника"
        />
        <ReferenceSelect
          label="Автомат"
          reference="units"
          optionText="name"
          value={(record.unitId as number) ?? null}
          onChange={(v) => onChange('unitId', v)}
          placeholder="Выберите автомат"
        />
        <label className="flex items-center justify-between">
          <span className="text-sm font-medium text-[#1a1c1e]">Тех. сбои</span>
          <IOSSwitch
            checked={!!record.incidentNotificationsEnabled}
            onChange={(e) => onChange('incidentNotificationsEnabled', e.target.checked)}
          />
        </label>
        <label className="flex items-center justify-between">
          <span className="text-sm font-medium text-[#1a1c1e]">Вызов</span>
          <IOSSwitch
            checked={!!record.androidCallNotificationsEnabled}
            onChange={(e) => onChange('androidCallNotificationsEnabled', e.target.checked)}
          />
        </label>
        <label className="flex items-center justify-between">
          <span className="text-sm font-medium text-[#1a1c1e]">Активны</span>
          <IOSSwitch
            checked={!!record.active}
            onChange={(e) => onChange('active', e.target.checked)}
          />
        </label>
      </div>
    )}
  </AdminEditForm>
);
