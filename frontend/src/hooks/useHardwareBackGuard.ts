import { useEffect, useRef } from 'react';
import { matchPath, useLocation, useNavigate } from 'react-router-dom';

/**
 * Служебные страницы, которые не должны накапливаться в истории.
 * При нажатии «назад» с них происходит пропуск до ближайшей неслужебной страницы.
 */
const TRANSIENT_ROUTES = new Set(['/profile', '/notifications', '/login']);

function isTransientRoute(pathname: string): boolean {
  for (const route of TRANSIENT_ROUTES) {
    if (pathname === route || pathname.startsWith(`${route}/`)) return true;
  }
  return false;
}

/**
 * Возвращает канонический иерархический родительский путь для заданного pathname,
 * или null если уже находимся в корне.
 *
 * Иерархия приложения:
 *   /  →  /workshops/:id  →  /workshops/:id/units/:uid/*
 *
 * Служебные страницы (/profile, /notifications, /login) пропускаются —
 * возвращается родитель предыдущей неслужебной страницы.
 */
function getHierarchicalParent(pathname: string): string | null {
  // Служебные страницы — пропускаем, возвращаем маркер для дальнейшей обработки
  if (isTransientRoute(pathname)) {
    return '_SKIP_TRANSIENT_';
  }

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
      const fromPath = currentPathnameRef.current;
      const parent = getHierarchicalParent(fromPath);

      if (parent === null) {
        // Корень — позволяем платформе выполнить дефолтное действие (выход из TWA).
        return;
      }

      const poppedTo = window.location.pathname;

      // Если ушли со служебной страницы — пропускаем все служебные в истории
      if (parent === '_SKIP_TRANSIENT_') {
        if (isTransientRoute(poppedTo)) {
          // Продолжаем идти назад через replace (не накапливаем forward-историю)
          navigate(-1);
        } else {
          // Оказались на неслужебной странице — это правильный результат
        }
        return;
      }

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
