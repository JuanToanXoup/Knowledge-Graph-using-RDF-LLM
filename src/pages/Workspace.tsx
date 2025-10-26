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
  const [activeTab, setActiveTab] = useState<TabType>(graphId ? 'overview' : 'graphs');
  const [apiConnected, setApiConnected] = useState(false);
  const [graphInfo, setGraphInfo] = useState<any>(null);
  const isMobile = useIsMobile();
  const [sidebarOpen, setSidebarOpen] = useState(!isMobile);

  useEffect(() => {
    // Check API connection
    fetch('http://localhost:8000/graphs')
      .then(() => setApiConnected(true))
      .catch(() => setApiConnected(false));
  }, []);

  useEffect(() => {
    // Fetch graph info if graphId exists
    if (graphId) {
      fetch(`http://localhost:8000/graph/${graphId}`)
        .then((res) => res.json())
        .then(setGraphInfo)
        .catch(console.error);
    }
  }, [graphId]);

  useEffect(() => {
    // Auto-collapse sidebar on mobile
    setSidebarOpen(!isMobile);
  }, [isMobile]);

  const renderTab = () => {
    switch (activeTab) {
      case 'overview':
        return graphId ? <OverviewTab graphId={graphId} /> : <div className="p-8">Please select a graph from the My Graphs tab</div>;
      case 'search':
        return graphId ? <SearchTab graphId={graphId} /> : <div className="p-8">Please select a graph from the My Graphs tab</div>;
      case 'chat':
        return graphId ? <ChatTab graphId={graphId} /> : <div className="p-8">Please select a graph from the My Graphs tab</div>;
      case 'entity':
        return graphId ? <EntityTab graphId={graphId} /> : <div className="p-8">Please select a graph from the My Graphs tab</div>;
      case 'sparql':
        return graphId ? <SparqlTab graphId={graphId} /> : <div className="p-8">Please select a graph from the My Graphs tab</div>;
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
          filename={graphInfo?.filename || graphId || 'My Workspace'}
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
