import {
  List,
  Datagrid,
  TextField,
  ReferenceField,
  BooleanField,
  DateField,
  EditButton,
  Edit,
  SimpleForm,
  ReferenceInput,
  SelectInput,
  BooleanInput,
  Create,
  DeleteButton,
} from 'react-admin';

export const UserAssignmentList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <ReferenceField source="userId" reference="users" label="Сотрудник">
        <TextField source="fullName" />
      </ReferenceField>
      <ReferenceField source="unitId" reference="units" label="Аппарат">
        <TextField source="name" />
      </ReferenceField>
      <DateField source="assignedAt" label="Назначен" />
      <BooleanField source="active" label="Активен" />
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const UserAssignmentEdit = () => (
  <Edit>
    <SimpleForm>
      <ReferenceInput source="userId" reference="users">
        <SelectInput optionText="fullName" label="Сотрудник" />
      </ReferenceInput>
      <ReferenceInput source="unitId" reference="units">
        <SelectInput optionText="name" label="Аппарат" />
      </ReferenceInput>
      <BooleanInput source="active" label="Активен" />
    </SimpleForm>
  </Edit>
);

export const UserAssignmentCreate = () => (
  <Create>
    <SimpleForm>
      <ReferenceInput source="userId" reference="users">
        <SelectInput optionText="fullName" label="Сотрудник" />
      </ReferenceInput>
      <ReferenceInput source="unitId" reference="units">
        <SelectInput optionText="name" label="Аппарат" />
      </ReferenceInput>
      <BooleanInput source="active" label="Активен" defaultValue={true} />
    </SimpleForm>
  </Create>
);
