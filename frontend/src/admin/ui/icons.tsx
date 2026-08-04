import type { CSSProperties, HTMLAttributes } from 'react';

interface AdminIconProps extends HTMLAttributes<HTMLSpanElement> {
  /** Имя svg-файла (без расширения) в public/assets/admin. */
  name: string;
  size?: number;
}

/** Каталог, из которого раздаются svg-иконки админ-панели. */
const ICONS_BASE_URL = '/assets/admin/';

/**
 * Иконка админ-панели на основе svg-файла из public/assets/admin,
 * подключённого через CSS mask. Цвет берётся из currentColor
 * (backgroundColor), поэтому сохраняются все text-[#...]
 * и hover/active-состояния потребителей.
 */
export function AdminIcon({ name, size = 20, className = '', style, ...props }: AdminIconProps) {
  const maskUrl = `url("${ICONS_BASE_URL}${name}.svg")`;
  const maskStyle: CSSProperties = {
    width: size,
    height: size,
    backgroundColor: 'currentColor',
    WebkitMaskImage: maskUrl,
    maskImage: maskUrl,
    WebkitMaskRepeat: 'no-repeat',
    maskRepeat: 'no-repeat',
    WebkitMaskPosition: 'center',
    maskPosition: 'center',
    WebkitMaskSize: 'contain',
    maskSize: 'contain',
  };
  return (
    <span
      aria-hidden="true"
      className={`inline-block shrink-0 ${className}`}
      style={{ ...maskStyle, ...style }}
      {...props}
    />
  );
}

type NamedIconProps = Omit<AdminIconProps, 'name'>;

export function IconRoles(props: NamedIconProps) {
  return <AdminIcon name="roles" {...props} />;
}

export function IconWorkshops(props: NamedIconProps) {
  return <AdminIcon name="workshops" {...props} />;
}

export function IconDeviceTypes(props: NamedIconProps) {
  return <AdminIcon name="device-types" {...props} />;
}

export function IconUnits(props: NamedIconProps) {
  return <AdminIcon name="units" {...props} />;
}

export function IconDevices(props: NamedIconProps) {
  return <AdminIcon name="devices" {...props} />;
}

export function IconUserTie(props: NamedIconProps) {
  return <AdminIcon name="user-tie" {...props} />;
}

export function IconNotifications(props: NamedIconProps) {
  return <AdminIcon name="notifications" {...props} />;
}

export function IconBell(props: NamedIconProps) {
  return <AdminIcon name="bell" {...props} />;
}

export function IconPlus(props: NamedIconProps) {
  return <AdminIcon name="plus" {...props} />;
}

export function IconPencil(props: NamedIconProps) {
  return <AdminIcon name="pencil" {...props} />;
}

export function IconTrash(props: NamedIconProps) {
  return <AdminIcon name="trash" {...props} />;
}

export function IconChevronLeft(props: NamedIconProps) {
  return <AdminIcon name="chevron-left" {...props} />;
}

export function IconChevronRight(props: NamedIconProps) {
  return <AdminIcon name="chevron-right" {...props} />;
}

export function IconChevronDown(props: NamedIconProps) {
  return <AdminIcon name="chevron-down" {...props} />;
}

export function IconMenu(props: NamedIconProps) {
  return <AdminIcon name="menu" {...props} />;
}

export function IconSearch(props: NamedIconProps) {
  return <AdminIcon name="search" {...props} />;
}

export function IconFilter(props: NamedIconProps) {
  return <AdminIcon name="filter" {...props} />;
}

export function IconCheck(props: NamedIconProps) {
  return <AdminIcon name="check" {...props} />;
}

export function IconCopy(props: NamedIconProps) {
  return <AdminIcon name="copy" {...props} />;
}

export function IconKey(props: NamedIconProps) {
  return <AdminIcon name="key" {...props} />;
}

export function IconX(props: NamedIconProps) {
  return <AdminIcon name="x" {...props} />;
}

export function IconDotsVertical(props: NamedIconProps) {
  return <AdminIcon name="dots-vertical" {...props} />;
}

export function IconInbox(props: NamedIconProps) {
  return <AdminIcon name="inbox" {...props} />;
}

export function IconBooks(props: NamedIconProps) {
  return <AdminIcon name="books" {...props} />;
}

export function IconAlertCircle(props: NamedIconProps) {
  return <AdminIcon name="alert-circle" {...props} />;
}

export function IconRefresh(props: NamedIconProps) {
  return <AdminIcon name="refresh" {...props} />;
}

export function IconSettings(props: NamedIconProps) {
  return <AdminIcon name="settings" {...props} />;
}

export function IconSave(props: NamedIconProps) {
  return <AdminIcon name="save" {...props} />;
}

export function IconEye(props: NamedIconProps) {
  return <AdminIcon name="eye" {...props} />;
}

export function IconEyeOff(props: NamedIconProps) {
  return <AdminIcon name="eye-off" {...props} />;
}

export function IconPower(props: NamedIconProps) {
  return <AdminIcon name="power" {...props} />;
}

export function IconPowerOff(props: NamedIconProps) {
  return <AdminIcon name="power-off" {...props} />;
}
