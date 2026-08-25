import { SkeletonBlock } from './SkeletonBlock';

/**
 * Skeleton-плейсхолдер для вкладки «Журнал».
 * Воспроизводит структуру LogsTab: карточка активных ошибок.
 */
export function LogsTabSkeleton() {
  return (
    <div aria-hidden="true" className="card p-4 card-static mb-3">
      <SkeletonBlock
        height="18px"
        width="52%"
        borderRadius="6px"
        style={{ marginBottom: '18px' }}
      />
      {Array.from({ length: 2 }, (_, i) => (
        <div
          key={i}
          style={{
            padding: '10px 0',
            borderBottom: i < 1 ? '1px solid #f0f0f0' : 'none',
            display: 'flex',
            flexDirection: 'column',
            gap: '6px',
          }}
        >
          <SkeletonBlock height="13px" width="42%" borderRadius="4px" />
          <SkeletonBlock height="12px" width="68%" borderRadius="4px" />
        </div>
      ))}
    </div>
  );
}
