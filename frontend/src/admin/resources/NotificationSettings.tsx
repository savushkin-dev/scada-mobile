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

export const NotificationSettingsList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <ReferenceField source="userId" reference="users" label="Сотрудник">
        <TextField source="fullName" />
      </ReferenceField>
      <ReferenceField source="unitId" reference="units" label="Аппарат">
        <TextField source="name" />
      </ReferenceField>
      <BooleanField source="incidentNotificationsEnabled" label="Тех. сбои" />
      <BooleanField source="androidCallNotificationsEnabled" label="Вызов" />
      <BooleanField source="active" label="Активны" />
      <DateField source="updatedAt" label="Обновлено" />
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const NotificationSettingsEdit = () => (
  <Edit>
    <SimpleForm>
      <ReferenceInput source="userId" reference="users">
        <SelectInput optionText="fullName" label="Сотрудник" />
      </ReferenceInput>
      <ReferenceInput source="unitId" reference="units">
        <SelectInput optionText="name" label="Аппарат" />
      </ReferenceInput>
      <BooleanInput source="incidentNotificationsEnabled" label="Тех. сбои" />
      <BooleanInput source="androidCallNotificationsEnabled" label="Вызов" />
      <BooleanInput source="active" label="Активны" />
    </SimpleForm>
  </Edit>
);

export const NotificationSettingsCreate = () => (
  <Create>
    <SimpleForm>
      <ReferenceInput source="userId" reference="users">
        <SelectInput optionText="fullName" label="Сотрудник" />
      </ReferenceInput>
      <ReferenceInput source="unitId" reference="units">
        <SelectInput optionText="name" label="Аппарат" />
      </ReferenceInput>
      <BooleanInput source="incidentNotificationsEnabled" label="Тех. сбои" defaultValue={true} />
      <BooleanInput source="androidCallNotificationsEnabled" label="Вызов" defaultValue={true} />
      <BooleanInput source="active" label="Активны" defaultValue={true} />
    </SimpleForm>
  </Create>
);
