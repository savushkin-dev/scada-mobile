/**
 * WorkshopCardSkeleton — плейсхолдер для WorkshopCard.
 *
 * Повторяет форму реальной карточки: заголовок, две строки метаданных.
 * Используется вместо текста «Загрузка…» пока данные ещё не пришли.
 */

import { SkeletonBlock } from './SkeletonBlock';

interface Props {
  /** Количество карточек-плейсхолдеров. По умолчанию 3. */
  count?: number;
}

export function WorkshopCardSkeleton({ count = 3 }: Props) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => (
        <div
          key={i}
          aria-hidden="true"
          className="card card-static p-5"
          style={{ borderColor: 'transparent' }}
        >
          {/* Заголовок цеха */}
          <SkeletonBlock height="22px" width="65%" borderRadius="8px" />

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'flex-end',
              marginTop: '14px',
            }}
          >
            {/* Две строки метаданных */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', flex: 1 }}>
              <SkeletonBlock height="12px" width="55%" borderRadius="4px" />
              <SkeletonBlock height="12px" width="40%" borderRadius="4px" />
            </div>
          </div>
        </div>
      ))}
    </>
  );
}
