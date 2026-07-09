import { Layout, type LayoutProps } from 'react-admin';
import { AdminNavProvider } from './ui/AdminNavContext';
import { AdminSidebarDesktop } from './ui/AdminSidebarDesktop';
import { AdminMobileHeader } from './ui/AdminMobileHeader';
import { AdminBottomSheetMenu } from './ui/AdminBottomSheetMenu';
import { EmptyAppBar, EmptyMenu, EmptySidebar } from './ui/AdminEmptyLayoutParts';

export function AdminLayout(props: LayoutProps) {
  return (
    <AdminNavProvider>
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
                  paddingBottom: 2,
                  backgroundColor: '#f8f9fa',
                },
              }}
            />
          </div>
        </div>
        <AdminBottomSheetMenu />
      </div>
    </AdminNavProvider>
  );
}
