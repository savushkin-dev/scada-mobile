import { useGetList } from 'react-admin';
import { SearchableSelect } from './SearchableSelect';
import type { ReactNode } from 'react';

interface ReferenceSelectProps {
  reference: string;
  optionText: string;
  optionValue?: string;
  label?: ReactNode;
  value: string | number | (string | number)[] | null;
  onChange: (value: string | number | (string | number)[] | null) => void;
  placeholder?: string;
  multiple?: boolean;
  disabled?: boolean;
  error?: string;
  hint?: string;
}

export function ReferenceSelect({
  reference,
  optionText,
  optionValue = 'id',
  ...props
}: ReferenceSelectProps) {
  const { data, isLoading } = useGetList(reference, {
    pagination: { page: 1, perPage: 1000 },
    sort: { field: optionText, order: 'ASC' },
  });

  const options = (data ?? []).map((item: Record<string, unknown>) => ({
    id: item[optionValue] as string | number,
    label: String(item[optionText] ?? item[optionValue]),
  }));

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
