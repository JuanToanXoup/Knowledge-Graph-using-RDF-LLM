import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FolderOpen, Download, Trash2, Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';

interface Graph {
  graph_id: string;
  created_at: string;
  entities_count: number;
  relations_count: number;
}

export const MyGraphsTab = () => {
  const [graphs, setGraphs] = useState<Graph[]>([]);
  const [search, setSearch] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    fetch('http://localhost:8000/graphs')
      .then((res) => res.json())
      .then((data) => setGraphs(data.graphs || []))
      .catch(console.error);
  }, []);

  const handleDownload = (graphId: string) => {
    window.open(`http://localhost:8000/download_graph/${graphId}`, '_blank');
  };

  const filteredGraphs = graphs.filter((g) =>
    g.graph_id.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold mb-2">My Graphs</h2>
          <p className="text-muted-foreground">
            Manage your knowledge graphs
          </p>
        </div>
        <Button
          onClick={() => navigate('/')}
          className="gap-2 shadow-glow-primary hover:shadow-glow-hover"
        >
          <Upload className="w-4 h-4" />
          Upload New Document
        </Button>
      </div>

      {/* Search */}
      <Input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Search graphs..."
        className="max-w-md border-border focus:border-primary"
      />

      {/* Graphs Grid */}
      <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredGraphs.map((graph) => (
          <Card
            key={graph.graph_id}
            className="p-6 border-border hover:border-primary/50 transition-all group"
          >
            <div className="flex items-start justify-between mb-4">
              <div className="w-12 h-12 rounded-lg bg-primary/10 flex items-center justify-center">
                <FolderOpen className="w-6 h-6 text-primary" />
              </div>
            </div>

            <h3 className="text-lg font-semibold mb-2 truncate">
              {graph.graph_id}
            </h3>

            <div className="flex items-center gap-4 mb-4 text-sm text-muted-foreground">
              <span>{graph.entities_count} entities</span>
              <span>•</span>
              <span>{graph.relations_count} relations</span>
            </div>

            <p className="text-xs text-muted-foreground mb-4">
              Created: {new Date(graph.created_at).toLocaleDateString()}
            </p>

            <div className="flex items-center gap-2">
              <Button
                onClick={() => navigate(`/workspace/${graph.graph_id}`)}
                className="flex-1"
              >
                Open
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleDownload(graph.graph_id)}
              >
                <Download className="w-4 h-4" />
              </Button>
              <Button variant="outline" size="sm">
                <Trash2 className="w-4 h-4 text-destructive" />
              </Button>
            </div>
          </Card>
        ))}
      </div>

      {filteredGraphs.length === 0 && (
        <div className="text-center py-12">
          <p className="text-muted-foreground">No graphs found</p>
        </div>
      )}
    </div>
  );
};
