import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';

type AdminEntityType =
  | 'employee'
  | 'workshop'
  | 'role'
  | 'unit'
  | 'device'
  | 'device-catalog'
  | 'device-type'
  | 'user-notification-settings';

interface AdminEntityChangedEventDetail {
  entity: AdminEntityType;
  id?: string;
}

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
 * Слушает события изменений админ-данных (приходят из RootLayout по WebSocket)
 * и инвалидирует кэш React Admin для соответствующего ресурса.
 *
 * Компонент должен рендериться внутри <Admin>, где доступен QueryClientProvider.
 */
export function AdminLiveUpdater() {
  const queryClient = useQueryClient();

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<AdminEntityChangedEventDetail>).detail;
      if (!detail?.entity) return;

      const resource = ENTITY_TO_RESOURCE[detail.entity];
      if (!resource) return;

      void queryClient.invalidateQueries({ queryKey: [resource] });
      if (detail.id) {
        void queryClient.invalidateQueries({ queryKey: [resource, 'getOne', detail.id] });
      }
    };

    window.addEventListener('scada:admin-entity-changed', handler);
    return () => window.removeEventListener('scada:admin-entity-changed', handler);
  }, [queryClient]);

  return null;
}
