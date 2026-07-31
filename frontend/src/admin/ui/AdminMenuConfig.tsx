import {
  IconSettings,
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

/** Пункты бокового/мобильного меню админ-панели. */
export const adminMenuItems: AdminMenuItem[] = [
  { name: 'settings', label: 'Настройки', icon: <IconSettings size={20} /> },
  { name: 'notifications', label: 'Уведомления', icon: <IconNotifications size={20} /> },
];

/** Оперативные сущности, доступные из шапки и мобильного меню. */
export const adminOperationalItems: AdminMenuItem[] = [
  { name: 'users', label: 'Сотрудники', icon: <IconUsers size={20} /> },
  { name: 'units', label: 'Автоматы', icon: <IconUnits size={20} /> },
];

/** Справочники, доступные из раздела «Настройки → Справочники». */
export const adminReferenceItems: AdminMenuItem[] = [
  { name: 'roles', label: 'Роли', icon: <IconRoles size={20} /> },
  { name: 'workshops', label: 'Цеха', icon: <IconWorkshops size={20} /> },
  { name: 'device-types', label: 'Типы устройств', icon: <IconDeviceTypes size={20} /> },
  { name: 'device-catalog', label: 'Справочник устройств', icon: <IconDevices size={20} /> },
];
