import { List, Edit, Create } from 'react-admin';
import { useParams } from 'react-router-dom';
import { RoleList, RoleEdit, RoleCreate } from './Roles';
import { WorkshopList, WorkshopEdit, WorkshopCreate } from './Workshops';
import { DeviceTypeList, DeviceTypeEdit, DeviceTypeCreate } from './DeviceTypes';
import { DeviceCatalogList, DeviceCatalogEdit, DeviceCatalogCreate } from './DeviceCatalog';

const resourceMap: Record<
  string,
  {
    label: string;
    list: () => JSX.Element;
    edit: () => JSX.Element;
    create: (props: { onSuccessWithData?: (data: Record<string, unknown>) => void }) => JSX.Element;
  }
> = {
  roles: {
    label: 'Роли',
    list: RoleList,
    edit: RoleEdit,
    create: RoleCreate,
  },
  workshops: {
    label: 'Цеха',
    list: WorkshopList,
    edit: WorkshopEdit,
    create: WorkshopCreate,
  },
  'device-types': {
    label: 'Типы устройств',
    list: DeviceTypeList,
    edit: DeviceTypeEdit,
    create: DeviceTypeCreate,
  },
  'device-catalog': {
    label: 'Справочник устройств',
    list: DeviceCatalogList,
    edit: DeviceCatalogEdit,
    create: DeviceCatalogCreate,
  },
};

export function ReferenceListPage() {
  const { resource } = useParams<{ resource: string }>();
  const config = resourceMap[resource ?? ''];

  if (!config) return <div className="p-6 text-[#74777f]">Справочник не найден</div>;

  const ListComponent = config.list;

  return (
    <List resource={resource} actions={false} pagination={false}>
      <ListComponent />
    </List>
  );
}

export function ReferenceEditPage() {
  const { resource } = useParams<{ resource: string }>();
  const config = resourceMap[resource ?? ''];

  if (!config) return <div className="p-6 text-[#74777f]">Справочник не найден</div>;

  const EditComponent = config.edit;

  return (
    <Edit resource={resource} redirect={false}>
      <EditComponent />
    </Edit>
  );
}

export function ReferenceCreatePage() {
  const { resource } = useParams<{ resource: string }>();
  const config = resourceMap[resource ?? ''];

  if (!config) return <div className="p-6 text-[#74777f]">Справочник не найден</div>;

  const CreateComponent = config.create;

  return (
    <Create resource={resource} redirect={false}>
      <CreateComponent />
    </Create>
  );
}
