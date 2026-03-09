/**
 * UnitCardSkeleton — плейсхолдер для UnitCard.
 *
 * Повторяет форму реальной карточки: название аппарата и строку состояния.
 * Используется вместо текста «Загрузка…» пока данные цеха ещё не пришли.
 */

import { SKELETON_CARD_STYLE, UI_BEHAVIOR, UNIT_SKELETON_STATE_STYLE } from '../../config';
import { SkeletonBlock } from './SkeletonBlock';

interface Props {
  /** Количество карточек-плейсхолдеров. По умолчанию 4. */
  count?: number;
}

export function UnitCardSkeleton({ count = UI_BEHAVIOR.workshopSkeletonCount }: Props) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => (
        <div
          key={i}
          aria-hidden="true"
          className="card card-static p-4"
          style={SKELETON_CARD_STYLE}
        >
          {/* Название аппарата/линии */}
          <SkeletonBlock height="20px" width="72%" borderRadius="8px" />

          {/* Строка текущего состояния */}
          <div style={UNIT_SKELETON_STATE_STYLE}>
            <SkeletonBlock height="13px" width="48%" borderRadius="4px" />
          </div>
        </div>
      ))}
    </>
  );
}
