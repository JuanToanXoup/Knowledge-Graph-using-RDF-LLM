import { useEffect, useState } from 'react';
import { BarChart3, Network, Database, Layers } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';

interface GraphInfo {
  entities_count: number;
  relations_count: number;
  statistics: any;
}

interface OverviewTabProps {
  graphId: string;
}

export const OverviewTab = ({ graphId }: OverviewTabProps) => {
  const [info, setInfo] = useState<GraphInfo | null>(null);
  const [imageUrl, setImageUrl] = useState<string>('');
  const [showStats, setShowStats] = useState(false);

  useEffect(() => {
    // Fetch graph info
    fetch(`http://localhost:8000/graph/${graphId}`)
      .then((res) => res.json())
      .then(setInfo)
      .catch(console.error);

    // Set visualization URL
    setImageUrl(`http://localhost:8000/visualization/${graphId}`);
  }, [graphId]);

  const metrics = [
    {
      icon: Database,
      label: 'Total Entities',
      value: info?.entities_count || 0,
    },
    {
      icon: Network,
      label: 'Total Relations',
      value: info?.relations_count || 0,
    },
    {
      icon: Layers,
      label: 'Total Triples',
      value: ((info?.entities_count || 0) + (info?.relations_count || 0)),
    },
    {
      icon: BarChart3,
      label: 'Entity Types',
      value: info?.statistics?.entity_types_count || 0,
    },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-3xl font-bold mb-2">Overview</h2>
        <p className="text-muted-foreground">
          View key metrics and statistics for your knowledge graph
        </p>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {metrics.map((metric) => {
          const Icon = metric.icon;
          return (
            <Card key={metric.label} className="p-6 border-border hover:border-primary/50 transition-all">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm text-muted-foreground mb-2">
                    {metric.label}
                  </p>
                  <p className="text-4xl font-bold">{metric.value}</p>
                </div>
                <div className="w-12 h-12 rounded-lg bg-primary/10 flex items-center justify-center">
                  <Icon className="w-6 h-6 text-primary" />
                </div>
              </div>
            </Card>
          );
        })}
      </div>

      {/* Statistics */}
      {info?.statistics && (
        <Card className="p-6 border-border">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-xl font-semibold">Statistics</h3>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowStats(!showStats)}
            >
              {showStats ? 'Hide' : 'Show'} JSON
            </Button>
          </div>
          {showStats && (
            <pre className="p-4 rounded-lg bg-secondary text-sm overflow-auto max-h-96">
              {JSON.stringify(info.statistics, null, 2)}
            </pre>
          )}
        </Card>
      )}

      {/* Graph Visualization */}
      <Card className="p-6 border-border">
        <h3 className="text-xl font-semibold mb-4">Graph Visualization</h3>
        <div className="relative rounded-lg overflow-hidden bg-secondary">
          {imageUrl && (
            <img
              src={imageUrl}
              alt="Knowledge Graph"
              className="w-full"
              onError={(e) => {
                e.currentTarget.src = '';
                e.currentTarget.alt = 'Visualization not available';
              }}
            />
          )}
          <div className="absolute bottom-4 right-4 flex gap-2">
            <Button size="sm" variant="secondary">
              Zoom In
            </Button>
            <Button size="sm" variant="secondary">
              Zoom Out
            </Button>
            <Button
              size="sm"
              onClick={() => window.open(imageUrl, '_blank')}
            >
              Download
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
};
