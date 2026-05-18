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

export const UnitList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <TextField source="name" label="Название" />
      <ReferenceField source="workshopId" reference="workshops" label="Цех">
        <TextField source="name" />
      </ReferenceField>
      <TextField source="printsrvInstanceId" label="PrintSrv ID" />
      <BooleanField source="active" label="Активен" />
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const UnitEdit = () => (
  <Edit>
    <SimpleForm>
      <TextInput source="name" label="Название аппарата" />
      <ReferenceInput source="workshopId" reference="workshops">
        <SelectInput optionText="name" label="Цех" />
      </ReferenceInput>
      <TextInput source="printsrvInstanceId" label="PrintSrv ID" />
      <BooleanInput source="active" label="Активен" />
    </SimpleForm>
  </Edit>
);

export const UnitCreate = () => (
  <Create>
    <SimpleForm>
      <TextInput source="name" label="Название аппарата" />
      <ReferenceInput source="workshopId" reference="workshops">
        <SelectInput optionText="name" label="Цех" />
      </ReferenceInput>
      <TextInput source="printsrvInstanceId" label="PrintSrv ID" />
      <BooleanInput source="active" label="Активен" defaultValue={true} />
    </SimpleForm>
  </Create>
);
