import { BOTTOM_NAV_ITEMS, DETAIL_TABS, UI_BEHAVIOR } from '../config';
import type { TabId } from '../types';

interface Props {
  activeTab: TabId;
  onTabChange: (tab: TabId) => void;
  errorCount: number;
  /** Дополнительный CSS-класс — используется для трансформации в боковую панель на десктопе (.details-nav). */
  className?: string;
}

export function BottomNav({ activeTab, onTabChange, errorCount, className }: Props) {
  return (
    <nav className={`bottom-nav${className ? ` ${className}` : ''}`}>
      {BOTTOM_NAV_ITEMS.map(({ tab, icon, label }) => {
        const isLogsTab = tab === DETAIL_TABS.logs;
        return (
          <div
            key={tab}
            className={`nav-item ${activeTab === tab ? 'active' : ''}`}
            onClick={() => onTabChange(tab)}
          >
            <span className="nav-icon">
              {icon}
              {isLogsTab && errorCount > UI_BEHAVIOR.emptyCollectionSize && (
                <span className="nav-badge">{errorCount}</span>
              )}
            </span>
            <span>{label}</span>
          </div>
        );
      })}
    </nav>
  );
}
