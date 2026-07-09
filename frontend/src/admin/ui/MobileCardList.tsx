import type { ReactNode } from 'react';

interface MobileCardListProps<T> {
  records: T[];
  renderCard: (record: T, index: number) => ReactNode;
  empty?: ReactNode;
}

export function MobileCardList<T>({ records, renderCard, empty }: MobileCardListProps<T>) {
  if (records.length === 0) {
    return <>{empty}</>;
  }

  return (
    <div className="flex flex-col gap-3 lg:hidden">
      {records.map((record, index) => (
        <div key={(record as { id?: string | number }).id ?? index}>
          {renderCard(record, index)}
        </div>
      ))}
    </div>
  );
}
