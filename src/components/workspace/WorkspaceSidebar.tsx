import {
  BarChart3,
  Search,
  MessageSquare,
  Network,
  Code,
  FolderOpen,
  Settings,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { TabType } from '@/pages/Workspace';
import { Button } from '@/components/ui/button';

interface WorkspaceSidebarProps {
  activeTab: TabType;
  onTabChange: (tab: TabType) => void;
  isOpen: boolean;
  onToggle: () => void;
  filename: string;
}

const menuItems = [
  { id: 'overview' as TabType, icon: BarChart3, label: 'Overview' },
  { id: 'search' as TabType, icon: Search, label: 'Semantic Search' },
  { id: 'chat' as TabType, icon: MessageSquare, label: 'Chat & Q&A' },
  { id: 'entity' as TabType, icon: Network, label: 'Entity Explorer' },
  { id: 'sparql' as TabType, icon: Code, label: 'SPARQL Query' },
  { id: 'graphs' as TabType, icon: FolderOpen, label: 'My Graphs' },
  { id: 'settings' as TabType, icon: Settings, label: 'Settings' },
];

export const WorkspaceSidebar = ({
  activeTab,
  onTabChange,
  isOpen,
  onToggle,
  filename,
}: WorkspaceSidebarProps) => {
  return (
    <aside
      className={`fixed left-0 top-24 h-[calc(100vh-6rem)] transition-all duration-300 ${
        isOpen ? 'w-64' : 'w-20'
      }`}
      style={{
        background: 'var(--glass-bg)',
        backdropFilter: 'blur(12px)',
        borderRight: '1px solid var(--glass-border)',
      }}
    >
      <div className="flex flex-col h-full">
        {/* Header */}
        <div className="p-4 border-b border-border flex items-center justify-between">
          {isOpen && (
            <div className="flex-1 min-w-0">
              <p className="text-xs text-muted-foreground">Current Graph</p>
              <p className="text-sm font-medium truncate">{filename}</p>
            </div>
          )}
          <Button
            variant="ghost"
            size="sm"
            onClick={onToggle}
            className="shrink-0"
          >
            {isOpen ? (
              <ChevronLeft className="w-4 h-4" />
            ) : (
              <ChevronRight className="w-4 h-4" />
            )}
          </Button>
        </div>

        {/* Menu Items */}
        <nav className="flex-1 p-2 space-y-1 overflow-y-auto">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;

            return (
              <button
                key={item.id}
                onClick={() => onTabChange(item.id)}
                className={`w-full flex items-center gap-3 px-3 py-3 rounded-lg transition-all ${
                  isActive
                    ? 'bg-primary/10 text-primary border-l-2 border-primary'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                }`}
              >
                <Icon className="w-5 h-5 shrink-0" />
                {isOpen && <span className="text-sm font-medium">{item.label}</span>}
              </button>
            );
          })}
        </nav>
      </div>
    </aside>
  );
};
