import { SkeletonBlock } from './SkeletonBlock';

/**
 * Skeleton-плейсхолдер для вкладки «Очередь».
 * Воспроизводит структуру QueueTab: заголовок + 3 позиции очереди.
 */
export function QueueTabSkeleton() {
  return (
    <div aria-hidden="true" className="card p-5 card-static mb-4">
      <SkeletonBlock
        height="18px"
        width="45%"
        borderRadius="6px"
        style={{ marginBottom: '18px' }}
      />

      {Array.from({ length: 3 }, (_, i) => (
        <div
          key={i}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '14px',
            padding: '10px 0',
            borderBottom: i < 2 ? '1px solid #f0f0f0' : 'none',
          }}
        >
          <SkeletonBlock height="36px" width="36px" borderRadius="50%" />
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '7px' }}>
            <SkeletonBlock height="13px" width="62%" borderRadius="4px" />
            <SkeletonBlock height="11px" width="82%" borderRadius="4px" />
          </div>
        </div>
      ))}
    </div>
  );
}
