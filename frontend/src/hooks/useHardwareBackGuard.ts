import { useEffect, useRef } from 'react';
import { matchPath, useLocation, useNavigate } from 'react-router-dom';

/**
 * Возвращает канонический иерархический родительский путь для заданного pathname,
 * или null если уже находимся в корне.
 *
 * Иерархия приложения:
 *   /  →  /workshops/:id  →  /workshops/:id/units/:uid/*
 */
function getHierarchicalParent(pathname: string): string | null {
  // Уровень деталей аппарата (включая любую вкладку) → уровень цеха
  const detailMatch = matchPath('/workshops/:workshopId/units/:unitId/*', pathname);
  if (detailMatch?.params.workshopId) {
    return `/workshops/${detailMatch.params.workshopId}`;
  }

  // Уровень цеха → корень
  const workshopMatch = matchPath('/workshops/:workshopId', pathname);
  if (workshopMatch) {
    return '/';
  }

  return null; // корневая страница
}

/**
 * Перехватывает события `popstate` (кнопка «назад» в Android / браузере)
 * и направляет к **иерархическому** родителю текущего экрана вместо
 * произвольного предыдущего URL в стеке браузерной истории.
 *
 * Это гарантирует, что:
 * - /workshops/:id            → назад → /
 * - /workshops/:id/units/:uid → любая вкладка → назад → /workshops/:id
 *   независимо от того, сколько раз пользователь переключал вкладки
 *   или нажимал кнопку «←» в шапке (которая добавляет запись в историю).
 *
 * Для корневой страницы (/) хук не вмешивается и позволяет платформе
 * выполнить дефолтное действие (выход из TWA / закрытие вкладки браузера).
 *
 * Должен вызываться в RootLayoutInner — единственный раз на всё приложение.
 */
export function useHardwareBackGuard(): void {
  const navigate = useNavigate();
  const location = useLocation();

  // Ref обновляется **синхронно** во время рендера React, поэтому в момент
  // срабатывания обработчика popstate он содержит путь ДО нажатия «назад».
  const currentPathnameRef = useRef<string>(location.pathname);
  currentPathnameRef.current = location.pathname;

  useEffect(() => {
    const handlePopState = (): void => {
      // window.location уже изменился на URL после pop-навигации.
      // currentPathnameRef сохранил страницу, с которой нажали «назад».
      const parent = getHierarchicalParent(currentPathnameRef.current);

      if (parent === null) {
        // Корень — позволяем платформе выполнить дефолтное действие (выход из TWA).
        return;
      }

      const poppedTo = window.location.pathname;

      // Браузер сам оказался на правильном иерархическом родителе
      // (нормальный flow с чистой историей) — не вмешиваемся.
      if (poppedTo === parent) return;

      // История «загрязнена» (например, накопились дубли после переходов
      // кнопкой «←» в шапке или пользователь открыл глубокую ссылку).
      // Заменяем текущую запись правильным иерархическим родителем.
      navigate(parent, { replace: true });
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [navigate]);
}
