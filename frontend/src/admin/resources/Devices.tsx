import {
  List,
  Datagrid,
  TextField,
  ReferenceField,
  EditButton,
  Edit,
  SimpleForm,
  TextInput,
  ReferenceInput,
  SelectInput,
  Create,
  DeleteButton,
} from 'react-admin';

export const DeviceList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <TextField source="code" label="Код" />
      <TextField source="displayName" label="Отображаемое имя" />
      <ReferenceField source="unitId" reference="units" label="Аппарат">
        <TextField source="name" />
      </ReferenceField>
      <ReferenceField source="typeId" reference="device-types" label="Тип">
        <TextField source="name" />
      </ReferenceField>
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const DeviceEdit = () => (
  <Edit>
    <SimpleForm>
      <TextInput source="code" label="Код устройства" />
      <TextInput source="displayName" label="Отображаемое имя" />
      <ReferenceInput source="unitId" reference="units">
        <SelectInput optionText="name" label="Аппарат" />
      </ReferenceInput>
      <ReferenceInput source="typeId" reference="device-types">
        <SelectInput optionText="name" label="Тип устройства" />
      </ReferenceInput>
    </SimpleForm>
  </Edit>
);

export const DeviceCreate = () => (
  <Create>
    <SimpleForm>
      <TextInput source="code" label="Код устройства" />
      <TextInput source="displayName" label="Отображаемое имя" />
      <ReferenceInput source="unitId" reference="units">
        <SelectInput optionText="name" label="Аппарат" />
      </ReferenceInput>
      <ReferenceInput source="typeId" reference="device-types">
        <SelectInput optionText="name" label="Тип устройства" />
      </ReferenceInput>
    </SimpleForm>
  </Create>
);
