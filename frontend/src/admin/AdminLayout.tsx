import { Layout, type LayoutProps } from 'react-admin';

/**
 * Кастомный Layout для react-admin.
 *
 * Проблема: стандартный RaLayout не ограничивает высоту content-области,
 * поэтому на небольших экранах пагинация таблиц и кнопки форм (Save/Delete)
 * уезжают за нижний край viewport и становятся недоступны.
 *
 * Решение: через sx-проп переопределяем стили content-области:
 *  - contentWithSidebar занимает всю доступную высоту (height: 100%)
 *  - content получает overflow-y: auto и скрывает горизонтальный скролл
 *
 * При этом левая sidebar (RaSidebar) остаётся position: fixed
 * и не прокручивается — прокручивается только правая часть.
 */
export function AdminLayout(props: LayoutProps) {
  return (
    <Layout
      {...props}
      appBar={() => null}
      sx={{
        // Корневой layout — занимает всю высоту, не даём расти за пределы viewport
        minHeight: '100%',
        height: '100%',

        [`& .RaLayout-appFrame`]: {
          display: 'flex',
          flexDirection: 'column',
          flexGrow: 1,
          // Убираем marginTop, который отводится под AppBar — в нашей админке
          // шапка приложения (PageHeader) уже рендерится снаружи через RootLayout,
          // поэтому дополнительный отступ сверху не нужен.
          marginTop: 0,
          height: '100%',
          minHeight: '100%',
        },

        [`& .RaLayout-contentWithSidebar`]: {
          display: 'flex',
          flexGrow: 1,
          // Ограничиваем высоту, чтобы content мог скроллиться внутри себя
          height: '100%',
          minHeight: 0,
        },

        [`& .RaLayout-content`]: {
          flexGrow: 1,
          flexBasis: 0,
          // Включаем вертикальную прокрутку только правой части
          overflowY: 'auto',
          overflowX: 'hidden',
          // Небольшой отступ снизу, чтобы последние элементы не прилипали к краю
          paddingBottom: 2,
        },
      }}
    />
  );
}
