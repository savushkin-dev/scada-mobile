import type { TabId } from '../types';

interface NavItemConfig {
  tab: TabId;
  icon: string;
  label: string;
}

const NAV_ITEMS: NavItemConfig[] = [
  { tab: 'tab-batch', icon: '📦', label: 'Партия' },
  { tab: 'tab-devices', icon: '⚙️', label: 'Устройства' },
  { tab: 'tab-queue', icon: '📋', label: 'Очередь' },
  { tab: 'tab-logs', icon: '⚠️', label: 'Журнал' },
];

interface Props {
  activeTab: TabId;
  onTabChange: (tab: TabId) => void;
  errorCount: number;
}

export function BottomNav({ activeTab, onTabChange, errorCount }: Props) {
  return (
    <nav className="bottom-nav">
      {NAV_ITEMS.map(({ tab, icon, label }) => {
        const isLogsTab = tab === 'tab-logs';
        return (
          <div
            key={tab}
            className={`nav-item ${activeTab === tab ? 'active' : ''}`}
            onClick={() => onTabChange(tab)}
          >
            <span className="nav-icon">
              {icon}
              {isLogsTab && errorCount > 0 && <span className="nav-badge">{errorCount}</span>}
            </span>
            <span>{label}</span>
          </div>
        );
      })}
    </nav>
  );
}
