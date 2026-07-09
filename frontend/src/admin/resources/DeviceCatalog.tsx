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
  FunctionField,
} from 'react-admin';
import { Chip } from '@mui/material';

const DeviceStateChip = ({ active, configured }: { active: boolean; configured: boolean }) => {
  if (!configured) {
    return <Chip label="Требует настройки" size="small" color="warning" variant="outlined" />;
  }
  if (!active) {
    return <Chip label="Неактивно" size="small" color="default" variant="outlined" />;
  }
  return <Chip label="Активно" size="small" color="success" variant="outlined" />;
};

export const DeviceCatalogList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <TextField source="code" label="Код" />
      <TextField source="displayName" label="Отображаемое имя" />
      <ReferenceField source="typeId" reference="device-types" label="Тип">
        <TextField source="name" />
      </ReferenceField>
      <BooleanField source="active" label="Активно" />
      <FunctionField
        label="Состояние"
        render={(record: { active: boolean; typeId?: number | null }) => (
          <DeviceStateChip active={record.active} configured={record.typeId != null} />
        )}
      />
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const DeviceCatalogEdit = () => (
  <Edit>
    <SimpleForm>
      <TextInput source="code" label="Код устройства" />
      <TextInput source="displayName" label="Отображаемое имя" />
      <ReferenceInput source="typeId" reference="device-types">
        <SelectInput optionText="name" label="Тип устройства" />
      </ReferenceInput>
      <BooleanInput source="active" label="Активно" />
    </SimpleForm>
  </Edit>
);

export const DeviceCatalogCreate = () => (
  <Create>
    <SimpleForm>
      <TextInput source="code" label="Код устройства" />
      <TextInput source="displayName" label="Отображаемое имя" />
      <ReferenceInput source="typeId" reference="device-types">
        <SelectInput optionText="name" label="Тип устройства" />
      </ReferenceInput>
      <BooleanInput source="active" label="Активно" defaultValue={false} />
    </SimpleForm>
  </Create>
);
