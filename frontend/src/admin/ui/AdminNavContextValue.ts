import { createContext } from 'react';

export interface AdminNavContextValue {
  isMenuOpen: boolean;
  openMenu: () => void;
  closeMenu: () => void;
}

export const AdminNavContext = createContext<AdminNavContextValue | null>(null);
