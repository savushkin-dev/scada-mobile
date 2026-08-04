import {
  IconBooks,
  IconNotifications,
  IconUnits,
  IconUserTie as IconUsers,
  IconRoles,
  IconWorkshops,
  IconDeviceTypes,
  IconDevices,
} from './icons';

export interface AdminMenuItem {
  name: string;
  label: string;
  icon: React.ReactNode;
}

/**
 * Пункты бокового/мобильного меню админ-панели.
 * Раздел «Настройки» (маршрут /admin/settings) временно скрыт из навигации
 * (issue #49): внутри него пока только справочники, которые вынесены
 * в отдельный пункт. Вернуть пункт «Настройки», когда в разделе появится
 * что-то кроме справочников.
 */
export const adminMenuItems: AdminMenuItem[] = [
  {
    name: 'settings/references',
    label: 'Справочники',
    icon: <IconBooks size={20} />,
  },
  { name: 'notifications', label: 'Уведомления', icon: <IconNotifications size={20} /> },
];

/** Оперативные сущности, доступные из шапки и мобильного меню. */
export const adminOperationalItems: AdminMenuItem[] = [
  { name: 'users', label: 'Сотрудники', icon: <IconUsers size={20} /> },
  { name: 'units', label: 'Автоматы', icon: <IconUnits size={20} /> },
];

/** Справочники системы (раздел «Справочники», /admin/settings/references). */
export const adminReferenceItems: AdminMenuItem[] = [
  { name: 'roles', label: 'Роли', icon: <IconRoles size={20} /> },
  { name: 'workshops', label: 'Цеха', icon: <IconWorkshops size={20} /> },
  { name: 'device-types', label: 'Типы устройств', icon: <IconDeviceTypes size={20} /> },
  { name: 'device-catalog', label: 'Справочник устройств', icon: <IconDevices size={20} /> },
];
