interface TruncatedChipListProps {
  items: string[] | undefined;
  max?: number;
}

/**
 * Список элементов в виде "чипов".
 *
 * Показывает не более `max` элементов; оставшиеся скрыты
 * за badge вида «+N». Если список пуст — «—».
 */
export function TruncatedChipList({ items, max = 2 }: TruncatedChipListProps) {
  if (!items || items.length === 0) {
    return <span className="text-secondary">—</span>;
  }

  const visible = items.slice(0, max);
  const hiddenCount = items.length - visible.length;

  return (
    <div className="flex flex-wrap items-center gap-1">
      {visible.map((item) => (
        <span
          key={item}
          className="inline-flex max-w-[8rem] items-center truncate rounded-full bg-gray-100 px-2 py-0.5 text-xs"
          title={item}
        >
          {item}
        </span>
      ))}
      {hiddenCount > 0 && (
        <span
          className="inline-flex items-center rounded-full bg-gray-200 px-2 py-0.5 text-xs font-medium"
          title={`Ещё ${hiddenCount}`}
        >
          +{hiddenCount}
        </span>
      )}
    </div>
  );
}
