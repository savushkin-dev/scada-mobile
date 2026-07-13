import { useState, type ReactNode } from 'react';
import { AdminNavContext } from './AdminNavContextValue';

export function AdminNavProvider({ children }: { children: ReactNode }) {
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  return (
    <AdminNavContext.Provider
      value={{
        isMenuOpen,
        openMenu: () => setIsMenuOpen(true),
        closeMenu: () => setIsMenuOpen(false),
      }}
    >
      {children}
    </AdminNavContext.Provider>
  );
}
