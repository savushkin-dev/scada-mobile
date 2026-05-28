import { AppBar, type AppBarProps } from 'react-admin';

/**
 * Кастомный AppBar для админки.
 *
 * На десктопе (>= lg) скрыт — шапка приложения (PageHeader) уже рендерится
 * снаружи через RootLayout.
 *
 * На мобильном (< lg) показывает минимальный прозрачный AppBar с гамбургером,
 * чтобы можно было открыть sidebar-меню.
 */
export function AdminAppBar(props: AppBarProps) {
  return (
    <AppBar
      {...props}
      sx={{
        // На десктопе полностью скрываем
        '@media (min-width: 1024px)': {
          display: 'none',
        },
        // На мобильном — компактная высота, прозрачный фон
        '@media (max-width: 1023px)': {
          minHeight: '44px',
          height: '44px',
          backgroundColor: 'transparent',
          boxShadow: 'none',
          color: '#1a1c1e',
          position: 'absolute',
          top: 0,
          left: 0,
          zIndex: 20,
        },
      }}
    />
  );
}
