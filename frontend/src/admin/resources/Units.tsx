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
  ReferenceArrayInput,
  SelectArrayInput,
  ReferenceManyField,
  useListContext,
} from 'react-admin';
import { Chip, Box } from '@mui/material';

const TruncatedChipList = ({ max = 6 }: { max?: number }) => {
  const { data, isLoading } = useListContext();
  if (isLoading) return <span>Загрузка...</span>;
  const items = data ?? [];
  const visible = items.slice(0, max);
  const hidden = items.length - max;

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, alignItems: 'center' }}>
      {visible.map((item: { id: number; displayName: string }) => (
        <Chip key={item.id} label={item.displayName} size="small" />
      ))}
      {hidden > 0 && <Chip label={`+${hidden}`} size="small" variant="outlined" />}
    </Box>
  );
};

export const UnitList = () => (
  <List>
    <Datagrid rowClick="edit">
      <TextField source="id" />
      <TextField source="name" label="Название" />
      <ReferenceField source="workshopId" reference="workshops" label="Цех">
        <TextField source="name" />
      </ReferenceField>
      <TextField source="printsrvInstanceId" label="PrintSrv ID" />
      <TextField source="printsrvHost" label="Хост" />
      <TextField source="printsrvPort" label="Порт" />
      <BooleanField source="active" label="Активен" />
      <ReferenceManyField reference="devices" target="unitId" label="Устройства">
        <TruncatedChipList max={6} />
      </ReferenceManyField>
      <EditButton />
      <DeleteButton />
    </Datagrid>
  </List>
);

export const UnitEdit = () => (
  <Edit>
    <SimpleForm>
      <TextInput source="name" label="Название автомата" />
      <ReferenceInput source="workshopId" reference="workshops">
        <SelectInput optionText="name" label="Цех" />
      </ReferenceInput>
      <TextInput source="printsrvInstanceId" label="PrintSrv ID" />
      <TextInput source="printsrvHost" label="Хост" />
      <TextInput source="printsrvPort" label="Порт" />
      <BooleanInput source="active" label="Активен" />
      <ReferenceArrayInput source="catalogIds" reference="device-catalog">
        <SelectArrayInput optionText="displayName" label="Устройства" />
      </ReferenceArrayInput>
    </SimpleForm>
  </Edit>
);

export const UnitCreate = () => (
  <Create>
    <SimpleForm>
      <TextInput source="name" label="Название автомата" />
      <ReferenceInput source="workshopId" reference="workshops">
        <SelectInput optionText="name" label="Цех" />
      </ReferenceInput>
      <TextInput source="printsrvInstanceId" label="PrintSrv ID" />
      <TextInput source="printsrvHost" label="PrintSrv хост" />
      <TextInput source="printsrvPort" label="PrintSrv порт" />
      <BooleanInput source="active" label="Активен" defaultValue={true} />
      <ReferenceArrayInput source="catalogIds" reference="device-catalog">
        <SelectArrayInput optionText="displayName" label="Устройства" />
      </ReferenceArrayInput>
    </SimpleForm>
  </Create>
);
