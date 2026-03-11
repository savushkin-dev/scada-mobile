import { useAppContext, type HeaderErrorSlot } from '../context/AppContext';
import type { AppError } from '../errors/AppError';

/**
 * Возвращает первую известную ошибку из перечисленных слотов шапки.
 *
 * ## Зачем нужен
 *
 * Экран часто загружает данные из нескольких источников параллельно
 * (например, REST topology + WebSocket unit). У каждого источника своя
 * стратегия повторных попыток — они попадают в ошибку в разное время.
 *
 * `usePageError` решает задачу **синхронизации**: как только ЛЮБОЙ канал
 * публикует ошибку в `headerErrors`, все секции экрана немедленно
 * переходят из skeleton-заглушки в состояние ошибки — не дожидаясь,
 * когда исчерпает попытки другой канал.
 *
 * ## Единственный источник правды
 *
 * `AppState.headerErrors` — глобальный агрегатор ошибок каналов.
 * Шапка (`HeaderErrorIndicator`) уже читает его для отображения "Ошибка".
 * Этот хук позволяет секциям страницы читать тот же источник,
 * обеспечивая консистентность между шапкой и телом страницы.
 *
 * ## Порядок слотов = приоритет
 *
 * Возвращается ошибка первого найденного слота.
 * Рекомендуется ставить более "живые" каналы (WS) раньше статических (REST),
 * чтобы пользователь видел наиболее актуальную причину ошибки.
 *
 * @example
 * // Экран деталей аппарата: WS unit + REST topology
 * const pageError = usePageError(['unit', 'topology']);
 *
 * @example
 * // Главный экран / список цехов: WS live + REST topology
 * const pageError = usePageError(['live', 'topology']);
 */
export function usePageError(slots: readonly HeaderErrorSlot[]): AppError | null {
  const { state } = useAppContext();

  for (const slot of slots) {
    const entry = state.headerErrors[slot];
    if (entry) return entry.error;
  }

  return null;
}
