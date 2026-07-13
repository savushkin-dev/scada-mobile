import { useGetList } from 'react-admin';

export function useNameMap(
  resource: string,
  field = 'name'
): (id: string | number | undefined) => string {
  const { data } = useGetList(resource, {
    pagination: { page: 1, perPage: 1000 },
    sort: { field, order: 'ASC' },
  });

  return (id) => {
    if (id == null) return '—';
    const item = (data ?? []).find((r: Record<string, unknown>) => String(r.id) === String(id));
    return item ? String(item[field] ?? id) : String(id);
  };
}
