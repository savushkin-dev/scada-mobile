import { useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { ADMIN_NOTIFICATION_EVENT, useAdminLiveWs, type AdminEntityType } from './useAdminLiveWs';

const ENTITY_TO_RESOURCE: Record<AdminEntityType, string> = {
  employee: 'users',
  workshop: 'workshops',
  role: 'roles',
  unit: 'units',
  device: 'devices',
  'device-catalog': 'device-catalog',
  'device-type': 'device-types',
  'user-notification-settings': 'user-notification-settings',
};

/**
 * Держит данные админ-панели актуальными без перезагрузки страницы:
 * подписывается на единый канал /ws/live и инвалидирует кэш React Admin
 * (react-query) при изменениях сущностей, сделанных другими сессиями/админами.
 *
 * Компонент должен рендериться внутри <Admin>, где доступен QueryClientProvider.
 */
export function AdminLiveUpdater() {
  const queryClient = useQueryClient();
  const { logout } = useAuth();

  const onEntityChanged = useCallback(
    (entity: AdminEntityType, id?: string) => {
      const resource = ENTITY_TO_RESOURCE[entity];
      if (!resource) return;

      void queryClient.invalidateQueries({ queryKey: [resource] });
      if (id) {
        void queryClient.invalidateQueries({ queryKey: [resource, 'getOne', id] });
      }
    },
    [queryClient]
  );

  const onAdminNotification = useCallback(() => {
    // Обновить открытый список уведомлений и счётчик непрочитанных в шапке.
    void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    window.dispatchEvent(new CustomEvent(ADMIN_NOTIFICATION_EVENT));
  }, [queryClient]);

  useAdminLiveWs({ onEntityChanged, onAdminNotification, onForceLogout: logout });

  return null;
}
