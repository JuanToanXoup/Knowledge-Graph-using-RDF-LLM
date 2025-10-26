import { Link, useLocation } from 'react-router-dom';
import { Brain, LayoutDashboard, Home } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface HeaderProps {
  showNav?: boolean;
  apiConnected?: boolean;
}

export const Header = ({ showNav = true, apiConnected }: HeaderProps) => {
  const location = useLocation();

  return (
    <header className="fixed top-4 left-1/2 -translate-x-1/2 z-50 w-[95%] max-w-7xl">
      <div
        className="px-6 py-4 rounded-2xl border border-[var(--glass-border)]"
        style={{
          background: 'var(--glass-bg)',
          backdropFilter: 'blur(12px)',
        }}
      >
        <div className="flex items-center justify-between">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2 group">
            <Brain className="w-6 h-6 text-primary" />
            <span className="text-xl font-bold bg-gradient-ember bg-clip-text text-transparent">
              KnowledgeGraph.AI
            </span>
          </Link>

          {/* Navigation */}
          {showNav ? (
            <nav className="hidden md:flex items-center gap-2">
              {['Home', 'Features', 'How it Works'].map((item) => {
                const isActive = item === 'Home' && location.pathname === '/';
                return (
                  <a
                    key={item}
                    href={`#${item.toLowerCase().replace(/\s+/g, '-')}`}
                    className={`px-4 py-2 rounded-lg transition-all ${
                      isActive
                        ? 'bg-primary text-primary-foreground'
                        : 'hover:bg-muted text-muted-foreground'
                    }`}
                  >
                    {item}
                  </a>
                );
              })}
              <Link to="/workspace">
                <Button variant="outline" size="sm" className="gap-2">
                  <LayoutDashboard className="w-4 h-4" />
                  Workspace
                </Button>
              </Link>
            </nav>
          ) : (
            <div className="flex items-center gap-2">
              <Link to="/">
                <Button variant="outline" size="sm" className="gap-2">
                  <Home className="w-4 h-4" />
                  Home
                </Button>
              </Link>
              <Link to="/workspace">
                <Button variant="outline" size="sm" className="gap-2">
                  <LayoutDashboard className="w-4 h-4" />
                  My Graphs
                </Button>
              </Link>
            </div>
          )}

          {/* API Status Icon */}
          {apiConnected !== undefined && (
            <div
              className={`w-3 h-3 rounded-full ${
                apiConnected ? 'bg-green-500 animate-pulse' : 'bg-red-500'
              }`}
              title={apiConnected ? 'API Connected' : 'API Disconnected'}
            />
          )}
        </div>
      </div>
    </header>
  );
};
