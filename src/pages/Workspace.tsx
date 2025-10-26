import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { ParticleBackground } from '@/components/ParticleBackground';
import { Header } from '@/components/Header';
import { WorkspaceSidebar } from '@/components/workspace/WorkspaceSidebar';
import { OverviewTab } from '@/components/workspace/OverviewTab';
import { SearchTab } from '@/components/workspace/SearchTab';
import { ChatTab } from '@/components/workspace/ChatTab';
import { EntityTab } from '@/components/workspace/EntityTab';
import { SparqlTab } from '@/components/workspace/SparqlTab';
import { MyGraphsTab } from '@/components/workspace/MyGraphsTab';
import { useIsMobile } from '@/hooks/use-mobile';

export type TabType = 'overview' | 'search' | 'chat' | 'entity' | 'sparql' | 'graphs' | 'settings';

const Workspace = () => {
  const { graphId } = useParams();
  const [activeTab, setActiveTab] = useState<TabType>('overview');
  const [apiConnected, setApiConnected] = useState(false);
  const isMobile = useIsMobile();
  const [sidebarOpen, setSidebarOpen] = useState(!isMobile);

  useEffect(() => {
    // Check API connection
    fetch('http://localhost:8000/graphs')
      .then(() => setApiConnected(true))
      .catch(() => setApiConnected(false));
  }, []);

  useEffect(() => {
    // Auto-collapse sidebar on mobile
    setSidebarOpen(!isMobile);
  }, [isMobile]);

  const renderTab = () => {
    if (!graphId) return null;

    switch (activeTab) {
      case 'overview':
        return <OverviewTab graphId={graphId} />;
      case 'search':
        return <SearchTab graphId={graphId} />;
      case 'chat':
        return <ChatTab graphId={graphId} />;
      case 'entity':
        return <EntityTab graphId={graphId} />;
      case 'sparql':
        return <SparqlTab graphId={graphId} />;
      case 'graphs':
        return <MyGraphsTab />;
      default:
        return <div className="p-8">Coming soon...</div>;
    }
  };

  return (
    <div className="min-h-screen">
      <ParticleBackground />
      <Header showNav={false} apiConnected={apiConnected} />

      <div className="pt-20 sm:pt-24 flex">
        <WorkspaceSidebar
          activeTab={activeTab}
          onTabChange={(tab) => {
            setActiveTab(tab);
            if (isMobile) setSidebarOpen(false);
          }}
          isOpen={sidebarOpen}
          onToggle={() => setSidebarOpen(!sidebarOpen)}
          filename={graphId || 'Unknown'}
        />

        <main
          className={`flex-1 transition-all duration-300 ${
            sidebarOpen && !isMobile ? 'ml-64' : sidebarOpen && isMobile ? 'ml-0' : 'ml-0 sm:ml-20'
          }`}
        >
          <div className="container py-6 sm:py-8 px-4">{renderTab()}</div>
        </main>
      </div>
    </div>
  );
};

export default Workspace;
