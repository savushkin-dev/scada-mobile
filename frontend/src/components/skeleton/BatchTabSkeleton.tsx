import { SkeletonBlock } from './SkeletonBlock';

/**
 * Skeleton-плейсхолдер для вкладки «Партия».
 * Воспроизводит структуру BatchTab: заголовок + 5 строк ключ-значение + кнопка.
 */
export function BatchTabSkeleton() {
  return (
    <div aria-hidden="true" className="card p-5 card-static mb-4">
      <SkeletonBlock
        height="18px"
        width="45%"
        borderRadius="6px"
        style={{ marginBottom: '18px' }}
      />

      {Array.from({ length: 5 }, (_, i) => (
        <div
          key={i}
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '9px 0',
            borderBottom: '1px solid #f0f0f0',
          }}
        >
          <SkeletonBlock height="13px" width="32%" borderRadius="4px" />
          <SkeletonBlock height="13px" width="40%" borderRadius="4px" />
        </div>
      ))}

      <SkeletonBlock height="30px" width="58%" borderRadius="8px" style={{ marginTop: '14px' }} />
    </div>
  );
}
