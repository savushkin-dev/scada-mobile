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

export const DeviceTypeList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <TextField source="code" label="Код" />
      <TextField source="name" label="Название" />
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const DeviceTypeEdit = () => (
  <Edit>
    <SimpleForm>
      <TextInput source="code" label="Код типа" />
      <TextInput source="name" label="Название типа" />
    </SimpleForm>
  </Edit>
);

export const DeviceTypeCreate = () => (
  <Create>
    <SimpleForm>
      <TextInput source="code" label="Код типа" />
      <TextInput source="name" label="Название типа" />
    </SimpleForm>
  </Create>
);
