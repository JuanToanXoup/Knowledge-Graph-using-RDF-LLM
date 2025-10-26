import { Link, useLocation } from 'react-router-dom';
import { Brain } from 'lucide-react';

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
          {showNav && (
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
            </nav>
          )}

          {/* Status Badge */}
          {apiConnected !== undefined && (
            <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-muted/50">
              <div
                className={`w-2 h-2 rounded-full ${
                  apiConnected ? 'bg-green-500 animate-pulse' : 'bg-red-500'
                }`}
              />
              <span className="text-sm text-muted-foreground">
                {apiConnected ? 'Connected' : 'Disconnected'}
              </span>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
