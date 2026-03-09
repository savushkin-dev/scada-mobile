/**
 * WorkshopCardSkeleton — плейсхолдер для WorkshopCard.
 *
 * Повторяет форму реальной карточки: заголовок, две строки метаданных.
 * Используется вместо текста «Загрузка…» пока данные ещё не пришли.
 */

import {
  SKELETON_CARD_STYLE,
  UI_BEHAVIOR,
  WORKSHOP_SKELETON_COLUMN_STYLE,
  WORKSHOP_SKELETON_ROW_STYLE,
} from '../../config';
import { SkeletonBlock } from './SkeletonBlock';

interface Props {
  /** Количество карточек-плейсхолдеров. По умолчанию 3. */
  count?: number;
}

export function WorkshopCardSkeleton({ count = UI_BEHAVIOR.dashboardSkeletonCount }: Props) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => (
        <div
          key={i}
          aria-hidden="true"
          className="card card-static p-5"
          style={SKELETON_CARD_STYLE}
        >
          {/* Заголовок цеха */}
          <SkeletonBlock height="22px" width="65%" borderRadius="8px" />

          <div style={WORKSHOP_SKELETON_ROW_STYLE}>
            {/* Две строки метаданных */}
            <div style={WORKSHOP_SKELETON_COLUMN_STYLE}>
              <SkeletonBlock height="12px" width="55%" borderRadius="4px" />
              <SkeletonBlock height="12px" width="40%" borderRadius="4px" />
            </div>
          </div>
        </div>
      ))}
    </>
  );
}
