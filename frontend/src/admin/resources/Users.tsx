import {
  List,
  Datagrid,
  TextField,
  ReferenceField,
  BooleanField,
  EditButton,
  Edit,
  SimpleForm,
  TextInput,
  ReferenceInput,
  SelectInput,
  BooleanInput,
  Create,
  DeleteButton,
  SelectArrayInput,
  useGetList,
  useRecordContext,
  FunctionField,
} from 'react-admin';
import { TruncatedChipList } from '../components/TruncatedChipList';
import { UserNotificationSettingsEditor } from '../components/UserNotificationSettingsEditor';

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

interface UnitChoice extends AdminUnit {
  assignedUserName: string | null;
  isAssignedToOther: boolean;
}

/**
 * Кастомный multi-select автоматов с индикацией занятости.
 *
 * - Свободные автоматы — обычным цветом.
 * - Закреплённые за другими сотрудниками — тусклее, с badge'ом справа
 *   с фамилией сотрудника, за которым закреплён.
 */
function UnitAssignmentArrayInput({ source }: { source: string }) {
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
    return <div className="text-secondary p-2">Загрузка автоматов...</div>;
  }

  const choices: UnitChoice[] = (units ?? []).map((unit) => {
    const assignment = (assignments ?? []).find((a) => a.unitId === unit.id && a.active);
    const assignedUser = assignment ? (users ?? []).find((u) => u.id === assignment.userId) : null;
    const assignedUserName = assignedUser?.fullName ?? null;
    const isAssignedToOther =
      assignedUserName != null && assignedUser != null && assignedUser.id !== currentUserId;

    return {
      ...unit,
      assignedUserName,
      isAssignedToOther,
    };
  });

  return (
    <SelectArrayInput
      source={source}
      choices={choices}
      optionText={(choice: UnitChoice) => (
        <span className={choice.isAssignedToOther ? 'text-secondary' : ''}>
          {choice.name}
          {choice.assignedUserName && (
            <span
              className="ml-2 text-xs"
              style={{ color: choice.isAssignedToOther ? '#888' : 'inherit' }}
            >
              {choice.isAssignedToOther ? `закреплён за ${choice.assignedUserName}` : `(ваш)`}
            </span>
          )}
        </span>
      )}
      optionValue="id"
      label="Автоматы"
    />
  );
}

export const UserList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <TextField source="code" label="Код сотрудника" />
      <TextField source="fullName" label="ФИО" />
      <ReferenceField source="roleId" reference="roles" label="Роль">
        <TextField source="name" />
      </ReferenceField>
      <BooleanField source="active" label="Активен" />
      <FunctionField
        label="Автоматы"
        render={(record: { unitNames?: string[] }) => (
          <TruncatedChipList items={record.unitNames} />
        )}
      />
      <FunctionField
        label="Тех. сбои"
        render={(record: { incidentNotificationsCount?: number }) =>
          record.incidentNotificationsCount ?? 0
        }
      />
      <FunctionField
        label="Вызов"
        render={(record: { callNotificationsCount?: number }) => record.callNotificationsCount ?? 0}
      />
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const UserEdit = () => (
  <Edit>
    <SimpleForm>
      <TextInput source="code" label="Код сотрудника" />
      <TextInput source="fullName" label="ФИО" />
      <TextInput
        source="password"
        label="Пароль (оставьте пустым, чтобы не менять)"
        type="password"
      />
      <ReferenceInput source="roleId" reference="roles">
        <SelectInput optionText="name" label="Роль" />
      </ReferenceInput>
      <BooleanInput source="active" label="Активен" />
      <UnitAssignmentArrayInput source="unitIds" />
      <UserNotificationSettingsEditor />
    </SimpleForm>
  </Edit>
);

export const UserCreate = () => (
  <Create>
    <SimpleForm>
      <TextInput source="code" label="Код сотрудника" />
      <TextInput source="fullName" label="ФИО" />
      <TextInput source="password" label="Пароль" type="password" />
      <ReferenceInput source="roleId" reference="roles">
        <SelectInput optionText="name" label="Роль" />
      </ReferenceInput>
      <BooleanInput source="active" label="Активен" defaultValue={true} />
      <UnitAssignmentArrayInput source="unitIds" />
      <UserNotificationSettingsEditor />
    </SimpleForm>
  </Create>
);
