import { useContext } from 'react';
import { AdminNavContext } from './AdminNavContextValue';

export function useAdminNav() {
  const ctx = useContext(AdminNavContext);
  if (!ctx) throw new Error('useAdminNav must be used within AdminNavProvider');
  return ctx;
}
