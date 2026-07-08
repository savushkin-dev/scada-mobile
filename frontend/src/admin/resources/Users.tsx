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
} from 'react-admin';

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
    </SimpleForm>
  </Create>
);
