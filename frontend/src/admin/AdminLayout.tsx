import { Layout, type LayoutProps } from 'react-admin';
import { useNavigate } from 'react-router-dom';
import { usePageHeader } from '../context/PageHeaderContext';
import { AdminNavProvider } from './ui/AdminNavContext';
import { AdminNotificationsProvider } from './ui/AdminNotificationsContext';
import { AdminSidebarDesktop } from './ui/AdminSidebarDesktop';
import { AdminMobileHeader } from './ui/AdminMobileHeader';
import { AdminBottomSheetMenu } from './ui/AdminBottomSheetMenu';
import { AdminLiveUpdater } from './AdminLiveUpdater';
import { EmptyAppBar, EmptyMenu, EmptySidebar } from './ui/AdminEmptyLayoutParts';

export function AdminLayout(props: LayoutProps) {
  const navigate = useNavigate();
  usePageHeader('Панель администратора', undefined, undefined, () => navigate('/'));

  return (
    <AdminNavProvider>
      <AdminNotificationsProvider>
        <AdminLiveUpdater />
        <div className="admin-app-root flex h-full min-h-0 flex-1 overflow-hidden">
          <AdminSidebarDesktop />
          <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
            <AdminMobileHeader />
            <div className="relative flex min-h-0 flex-1 flex-col overflow-hidden bg-[#f8f9fa]">
              <Layout
                {...props}
                appBar={EmptyAppBar}
                menu={EmptyMenu}
                sidebar={EmptySidebar}
                sx={{
                  minHeight: '100%',
                  height: '100%',
                  [`& .RaLayout-appFrame`]: {
                    display: 'flex',
                    flexDirection: 'column',
                    flexGrow: 1,
                    marginTop: 0,
                    paddingTop: 0,
                    height: '100%',
                    minHeight: '100%',
                  },
                  [`& .RaLayout-contentWithSidebar`]: {
                    display: 'flex',
                    flexGrow: 1,
                    height: '100%',
                    minHeight: 0,
                  },
                  [`& .RaLayout-content`]: {
                    flexGrow: 1,
                    flexBasis: 0,
                    overflowY: 'auto',
                    overflowX: 'hidden',
                    paddingTop: 0,
                    paddingBottom: 2,
                    backgroundColor: '#f8f9fa',
                  },
                }}
              />
            </div>
          </div>
          <AdminBottomSheetMenu />
        </div>
      </AdminNotificationsProvider>
    </AdminNavProvider>
  );
}
