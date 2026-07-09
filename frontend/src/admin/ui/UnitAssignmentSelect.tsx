import { useGetList, useRecordContext } from 'react-admin';
import { SearchableSelect } from './SearchableSelect';

interface AdminUnit {
  id: number;
  name: string;
}

interface UserAssignment {
  id: number;
  userId: number;
  unitId: number;
  active: boolean;
}

interface AdminUser {
  id: number;
  fullName: string;
}

interface UnitAssignmentSelectProps {
  value: number[];
  onChange: (value: number[]) => void;
}

export function UnitAssignmentSelect({ value, onChange }: UnitAssignmentSelectProps) {
  const record = useRecordContext<AdminUser>();
  const currentUserId = record?.id;

  const { data: units, isLoading: unitsLoading } = useGetList<AdminUnit>('units', {
    pagination: { page: 1, perPage: 1000 },
    sort: { field: 'name', order: 'ASC' },
  });
  const { data: assignments, isLoading: assignmentsLoading } = useGetList<UserAssignment>(
    'user-assignments',
    {
      pagination: { page: 1, perPage: 1000 },
      sort: { field: 'id', order: 'ASC' },
    }
  );
  const { data: users, isLoading: usersLoading } = useGetList<AdminUser>('users', {
    pagination: { page: 1, perPage: 1000 },
    sort: { field: 'fullName', order: 'ASC' },
  });

  if (unitsLoading || assignmentsLoading || usersLoading) {
    return (
      <div className="w-full">
        <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
          Автоматы
        </label>
        <div className="h-12 w-full animate-pulse rounded-[14px] bg-[#edeef0]" />
      </div>
    );
  }

  const options = (units ?? []).map((unit) => {
    const assignment = (assignments ?? []).find((a) => a.unitId === unit.id && a.active);
    const assignedUser = assignment ? (users ?? []).find((u) => u.id === assignment.userId) : null;
    const assignedUserName = assignedUser?.fullName ?? null;
    const isAssignedToOther =
      assignedUserName != null && assignedUser != null && assignedUser.id !== currentUserId;

    return {
      id: unit.id,
      label: unit.name,
      disabled: isAssignedToOther,
      suffix: assignedUserName ? (
        <span className="text-xs text-[#74777f]">
          {isAssignedToOther ? `закреплён за ${assignedUserName}` : 'ваш'}
        </span>
      ) : undefined,
    };
  });

  return (
    <SearchableSelect
      label="Автоматы"
      multiple
      options={options}
      value={value}
      onChange={(v) => onChange((v as number[]) ?? [])}
      placeholder="Выберите автоматы"
    />
  );
}
