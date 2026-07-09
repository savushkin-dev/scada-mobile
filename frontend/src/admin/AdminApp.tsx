import { Admin, Resource } from 'react-admin';
import { dataProvider } from './dataProvider';
import { AdminLayout } from './AdminLayout';
import { RoleList, RoleEdit, RoleCreate } from './resources/Roles';
import { WorkshopList, WorkshopEdit, WorkshopCreate } from './resources/Workshops';
import { DeviceTypeList, DeviceTypeEdit, DeviceTypeCreate } from './resources/DeviceTypes';
import { UnitList, UnitEdit, UnitCreate } from './resources/Units';
import {
  DeviceCatalogList,
  DeviceCatalogEdit,
  DeviceCatalogCreate,
} from './resources/DeviceCatalog';
import { UserList, UserEdit, UserCreate } from './resources/Users';
import { NotificationList } from './resources/Notifications';
import {
  NotificationSettingsList,
  NotificationSettingsEdit,
  NotificationSettingsCreate,
} from './resources/NotificationSettings';

export function AdminApp() {
  return (
    <div className="admin-app-root">
      <Admin dataProvider={dataProvider} basename="/admin" layout={AdminLayout}>
        <Resource
          name="roles"
          options={{ label: 'Роли' }}
          list={RoleList}
          edit={RoleEdit}
          create={RoleCreate}
        />
        <Resource
          name="workshops"
          options={{ label: 'Цеха' }}
          list={WorkshopList}
          edit={WorkshopEdit}
          create={WorkshopCreate}
        />
        <Resource
          name="device-types"
          options={{ label: 'Типы устройств' }}
          list={DeviceTypeList}
          edit={DeviceTypeEdit}
          create={DeviceTypeCreate}
        />
        <Resource
          name="units"
          options={{ label: 'Автоматы' }}
          list={UnitList}
          edit={UnitEdit}
          create={UnitCreate}
        />
        <Resource
          name="device-catalog"
          options={{ label: 'Справочник устройств' }}
          list={DeviceCatalogList}
          edit={DeviceCatalogEdit}
          create={DeviceCatalogCreate}
        />
        <Resource
          name="users"
          options={{ label: 'Сотрудники' }}
          list={UserList}
          edit={UserEdit}
          create={UserCreate}
        />
        <Resource
          name="user-notification-settings"
          options={{ label: 'Настройки уведомлений' }}
          list={NotificationSettingsList}
          edit={NotificationSettingsEdit}
          create={NotificationSettingsCreate}
        />
        <Resource name="notifications" options={{ label: 'Уведомления' }} list={NotificationList} />
      </Admin>
    </div>
  );
}
