import type { SVGProps } from 'react';

interface AdminIconProps extends SVGProps<SVGSVGElement> {
  size?: number;
}

export function AdminIcon({ size = 20, className = '', children, ...props }: AdminIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={`inline-block shrink-0 ${className}`}
      {...props}
    >
      {children}
    </svg>
  );
}

export function IconRoles(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </AdminIcon>
  );
}

export function IconWorkshops(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <path d="M2 22h20M2 6h20v16H2zM6 6V2h12v4M12 2v20" />
    </AdminIcon>
  );
}

export function IconDeviceTypes(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <polygon points="12 2 2 7 12 12 22 7 12 2" />
      <polyline points="2 17 12 22 22 17" />
      <polyline points="2 12 12 17 22 12" />
    </AdminIcon>
  );
}

export function IconUnits(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
      <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
      <line x1="12" y1="22.08" x2="12" y2="12" />
    </AdminIcon>
  );
}

export function IconDevices(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
      <line x1="8" y1="21" x2="16" y2="21" />
      <line x1="12" y1="17" x2="12" y2="21" />
    </AdminIcon>
  );
}

export function IconUserTie(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <circle cx="12" cy="7" r="4" />
      <path d="M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2" />
    </AdminIcon>
  );
}

export function IconNotifications(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </AdminIcon>
  );
}

export function IconBell(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </AdminIcon>
  );
}

export function IconPlus(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </AdminIcon>
  );
}

export function IconPencil(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z" />
    </AdminIcon>
  );
}

export function IconTrash(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <line x1="10" y1="11" x2="10" y2="17" />
      <line x1="14" y1="11" x2="14" y2="17" />
    </AdminIcon>
  );
}

export function IconChevronLeft(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <polyline points="15 18 9 12 15 6" />
    </AdminIcon>
  );
}

export function IconChevronRight(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <polyline points="9 18 15 12 9 6" />
    </AdminIcon>
  );
}

export function IconMenu(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <line x1="3" y1="12" x2="21" y2="12" />
      <line x1="3" y1="6" x2="21" y2="6" />
      <line x1="3" y1="18" x2="21" y2="18" />
    </AdminIcon>
  );
}

export function IconSearch(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <circle cx="11" cy="11" r="8" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
    </AdminIcon>
  );
}

export function IconCheck(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <polyline points="20 6 9 17 4 12" />
    </AdminIcon>
  );
}

export function IconX(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </AdminIcon>
  );
}

export function IconInbox(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <polyline points="22 12 16 12 14 15 10 15 8 12 2 12" />
      <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
    </AdminIcon>
  );
}

export function IconAlertCircle(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </AdminIcon>
  );
}

export function IconRefresh(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <polyline points="23 4 23 10 17 10" />
      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
    </AdminIcon>
  );
}

export function IconSave(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
      <polyline points="17 21 17 13 7 13 7 21" />
      <polyline points="7 3 7 8 15 8" />
    </AdminIcon>
  );
}

export function IconEye(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </AdminIcon>
  );
}

export function IconEyeOff(props: Omit<AdminIconProps, 'children'>) {
  return (
    <AdminIcon {...props}>
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </AdminIcon>
  );
}
