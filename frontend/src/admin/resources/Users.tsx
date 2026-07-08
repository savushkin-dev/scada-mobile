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

// Кастомный компонент для выбора автоматов с индикацией занятости
const UnitSelectArrayInput = () => {
  const record = useRecordContext();
  const { data: units, isLoading: unitsLoading } = useGetList('units', {
    pagination: { page: 1, perPage: 1000 },
  });
  const { data: assignments, isLoading: assignmentsLoading } = useGetList('user-assignments', {
    pagination: { page: 1, perPage: 1000 },
  });
  const { data: users, isLoading: usersLoading } = useGetList('users', {
    pagination: { page: 1, perPage: 1000 },
  });

  if (unitsLoading || assignmentsLoading || usersLoading) {
    return <span>Загрузка...</span>;
  }

  const choices = (units ?? []).map((unit: { id: number; name: string }) => {
    const assignment = (assignments ?? []).find(
      (a: { unitId: number; active: boolean; userId: number }) =>
        a.unitId === unit.id && a.active && a.userId !== record?.id
    );
    const assignedUser = assignment
      ? (users ?? []).find((u: { id: number; fullName: string }) => u.id === assignment.userId)
          ?.fullName
      : null;

    return {
      id: unit.id,
      name: assignedUser ? `${unit.name} (занят: ${assignedUser})` : unit.name,
    };
  });

  return (
    <SelectArrayInput
      source="unitIds"
      choices={choices}
      optionText="name"
      optionValue="id"
      label="Автоматы"
    />
  );
};

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
        render={(record: { unitNames?: string }) => record.unitNames || '-'}
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
      <UnitSelectArrayInput />
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
      <UnitSelectArrayInput />
    </SimpleForm>
  </Create>
);
