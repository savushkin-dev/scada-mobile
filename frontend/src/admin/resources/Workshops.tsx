import {
  List,
  Datagrid,
  TextField,
  BooleanField,
  EditButton,
  Edit,
  SimpleForm,
  TextInput,
  BooleanInput,
  Create,
  DeleteButton,
} from 'react-admin';

export const WorkshopList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <TextField source="name" label="Название" />
      <BooleanField source="active" label="Активен" />
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const WorkshopEdit = () => (
  <Edit>
    <SimpleForm>
      <TextInput source="name" label="Название цеха" />
      <BooleanInput source="active" label="Активен" />
    </SimpleForm>
  </Edit>
);

export const WorkshopCreate = () => (
  <Create>
    <SimpleForm>
      <TextInput source="name" label="Название цеха" />
      <BooleanInput source="active" label="Активен" defaultValue={true} />
    </SimpleForm>
  </Create>
);
