import { CARD_TITLE_BETWEEN_STYLE } from '../../config';
import { SkeletonBlock } from './SkeletonBlock';

/**
 * Skeleton-плейсхолдер для вкладки «Устройства».
 * Воспроизводит структуру DevicesTab: три групповые карточки с заголовком и строками.
 *
 * Вынесен в skeleton/ для единообразия с другими вкладками.
 */
export function DevicesTabSkeleton() {
  return (
    <>
      {Array.from({ length: 3 }, (_, i) => (
        <div key={i} aria-hidden="true" className="card p-5 card-static mb-4">
          <div style={CARD_TITLE_BETWEEN_STYLE}>
            <SkeletonBlock height="18px" width="55%" borderRadius="6px" />
            <SkeletonBlock height="22px" width="72px" borderRadius="12px" />
          </div>
          <div style={{ marginTop: '14px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <SkeletonBlock height="14px" width="80%" borderRadius="4px" />
            <SkeletonBlock height="14px" width="65%" borderRadius="4px" />
          </div>
        </div>
      ))}
    </>
  );
}
