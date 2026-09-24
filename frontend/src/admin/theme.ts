import { defaultTheme } from 'react-admin';

/*
  Админка — только светлая тема.
  Без явной привязки react-admin/MUI подхватывают prefers-color-scheme
  устройства и переключают половину интерфейса в тёмный режим.
*/
export const adminLightTheme = {
  ...defaultTheme,
  palette: { ...defaultTheme.palette, mode: 'light' as const },
};
