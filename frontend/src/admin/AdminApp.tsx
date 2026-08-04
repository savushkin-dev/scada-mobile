import { Admin, Resource, List, CustomRoutes } from 'react-admin';
import { Navigate, Route } from 'react-router-dom';
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
import { NotificationSettingsEdit } from './resources/NotificationSettings';
import { SettingsPage } from './resources/Settings';
import { ReferenceHubPage } from './resources/ReferenceHub';
import { AdminProfilePage } from './resources/AdminProfile';
import {
  ReferenceListPage,
  ReferenceEditPage,
  ReferenceCreatePage,
} from './resources/ReferencePages';

export function AdminApp() {
  return (
    <Admin dataProvider={dataProvider} basename="/admin" layout={AdminLayout}>
      <Resource
        name="roles"
        options={{ label: 'Роли' }}
        list={() => (
          <List actions={false} pagination={false}>
            <RoleList />
          </List>
        )}
        edit={RoleEdit}
        create={RoleCreate}
      />
      <Resource
        name="workshops"
        options={{ label: 'Цеха' }}
        list={() => (
          <List actions={false} pagination={false}>
            <WorkshopList />
          </List>
        )}
        edit={WorkshopEdit}
        create={WorkshopCreate}
      />
      <Resource
        name="device-types"
        options={{ label: 'Типы устройств' }}
        list={() => (
          <List actions={false} pagination={false}>
            <DeviceTypeList />
          </List>
        )}
        edit={DeviceTypeEdit}
        create={DeviceTypeCreate}
      />
      <Resource
        name="units"
        options={{ label: 'Автоматы' }}
        list={() => (
          <List actions={false} pagination={false}>
            <UnitList />
          </List>
        )}
        edit={UnitEdit}
        create={UnitCreate}
      />
      <Resource
        name="device-catalog"
        options={{ label: 'Справочник устройств' }}
        list={() => (
          <List actions={false} pagination={false}>
            <DeviceCatalogList />
          </List>
        )}
        edit={DeviceCatalogEdit}
        create={DeviceCatalogCreate}
      />
      <Resource
        name="users"
        options={{ label: 'Сотрудники' }}
        list={() => (
          <List actions={false} pagination={false}>
            <UserList />
          </List>
        )}
        edit={UserEdit}
        create={UserCreate}
      />
      <Resource name="user-notification-settings" edit={NotificationSettingsEdit} />
      <Resource
        name="notifications"
        options={{ label: 'Уведомления' }}
        list={() => (
          <List actions={false} pagination={false} empty={false}>
            <NotificationList />
          </List>
        )}
      />
      <CustomRoutes>
        <Route path="/" element={<Navigate to="/admin/users" replace />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/references" element={<ReferenceHubPage />} />
        <Route path="/settings/references/:resource" element={<ReferenceListPage />} />
        <Route path="/settings/references/:resource/create" element={<ReferenceCreatePage />} />
        <Route path="/settings/references/:resource/:id" element={<ReferenceEditPage />} />
        <Route path="/profile" element={<AdminProfilePage />} />
      </CustomRoutes>
    </Admin>
  );
}
