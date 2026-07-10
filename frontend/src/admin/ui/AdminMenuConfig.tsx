import {
  IconRoles,
  IconWorkshops,
  IconDeviceTypes,
  IconUnits,
  IconDevices,
  IconUserTie,
  IconNotifications,
} from './icons';

export interface AdminMenuItem {
  name: string;
  label: string;
  icon: React.ReactNode;
}

export const adminMenuItems: AdminMenuItem[] = [
  { name: 'roles', label: 'Роли', icon: <IconRoles size={20} /> },
  { name: 'workshops', label: 'Цеха', icon: <IconWorkshops size={20} /> },
  { name: 'device-types', label: 'Типы устройств', icon: <IconDeviceTypes size={20} /> },
  { name: 'units', label: 'Автоматы', icon: <IconUnits size={20} /> },
  { name: 'device-catalog', label: 'Справочник устройств', icon: <IconDevices size={20} /> },
  { name: 'users', label: 'Сотрудники', icon: <IconUserTie size={20} /> },
  { name: 'notifications', label: 'Уведомления', icon: <IconNotifications size={20} /> },
];
