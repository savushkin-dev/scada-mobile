import { useGetList } from 'react-admin';
import { SearchableSelect } from './SearchableSelect';
import type { ReactNode } from 'react';

interface ReferenceSelectProps {
  reference: string;
  optionText: string;
  /**
   * Вторичное поле записи, добавляемое в подпись опции в скобках:
   * «Название (Код)». Используется для справочников с неуникальными
   * названиями (например, device-catalog: name + code).
   */
  optionSecondary?: string;
  optionValue?: string;
  label?: ReactNode;
  value: string | number | (string | number)[] | null;
  onChange: (value: string | number | (string | number)[] | null) => void;
  placeholder?: string;
  multiple?: boolean;
  disabled?: boolean;
  error?: string;
  hint?: string;
  onAddNew?: () => void;
  addNewLabel?: string;
}

export function ReferenceSelect({
  reference,
  optionText,
  optionSecondary,
  optionValue = 'id',
  ...props
}: ReferenceSelectProps) {
  const { data, isLoading } = useGetList(reference, {
    pagination: { page: 1, perPage: 1000 },
    sort: { field: optionText, order: 'ASC' },
  });

  const options = (data ?? []).map((item: Record<string, unknown>) => {
    const primary = String(item[optionText] ?? item[optionValue]);
    const secondary = optionSecondary ? item[optionSecondary] : undefined;
    return {
      id: item[optionValue] as string | number,
      label: secondary != null && secondary !== '' ? `${primary} (${secondary})` : primary,
    };
  });

  if (isLoading) {
    return (
      <div className="w-full">
        {props.label && (
          <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
            {props.label}
          </label>
        )}
        <div className="h-12 w-full animate-pulse rounded-[14px] bg-[#edeef0]" />
      </div>
    );
  }

  return <SearchableSelect options={options} {...props} />;
}
