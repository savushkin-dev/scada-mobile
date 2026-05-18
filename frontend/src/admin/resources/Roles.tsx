import {
  List,
  Datagrid,
  TextField,
  EditButton,
  Edit,
  SimpleForm,
  TextInput,
  Create,
  DeleteButton,
} from 'react-admin';

export const RoleList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <TextField source="name" label="Название" />
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const RoleEdit = () => (
  <Edit>
    <SimpleForm>
      <TextInput source="name" label="Название роли" />
    </SimpleForm>
  </Edit>
);

export const RoleCreate = () => (
  <Create>
    <SimpleForm>
      <TextInput source="name" label="Название роли" />
    </SimpleForm>
  </Create>
);
