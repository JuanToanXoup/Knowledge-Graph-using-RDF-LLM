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
  const [imageLoading, setImageLoading] = useState(true);
  const [imageError, setImageError] = useState(false);

  useEffect(() => {
    // Fetch graph info
    fetch(`http://localhost:8000/graph/${graphId}`)
      .then((res) => res.json())
      .then((data) => {
        setInfo(data);
        console.log('Graph info loaded:', data);
      })
      .catch(console.error);

    // Set visualization URL
    const vizUrl = `http://localhost:8000/visualization/${graphId}`;
    setImageUrl(vizUrl);
    setImageLoading(true);
    setImageError(false);
    console.log('Visualization URL:', vizUrl);
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
      value: info?.statistics?.triples || ((info?.entities_count || 0) + (info?.relations_count || 0)),
    },
    {
      icon: BarChart3,
      label: 'Entity Types',
      value: info?.statistics?.entity_types_count || 0,
    },
  ];

  return (
    <div className="space-y-6 sm:space-y-8">
      <div>
        <h2 className="text-2xl sm:text-3xl font-bold mb-2">Overview</h2>
        <p className="text-sm sm:text-base text-muted-foreground">
          View key metrics and statistics for your knowledge graph
        </p>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
        {metrics.map((metric) => {
          const Icon = metric.icon;
          return (
            <Card key={metric.label} className="p-4 sm:p-6 border-border hover:border-primary/50 transition-all">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-xs sm:text-sm text-muted-foreground mb-1 sm:mb-2">
                    {metric.label}
                  </p>
                  <p className="text-2xl sm:text-3xl lg:text-4xl font-bold truncate">{metric.value}</p>
                </div>
                <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                  <Icon className="w-5 h-5 sm:w-6 sm:h-6 text-primary" />
                </div>
              </div>
            </Card>
          );
        })}
      </div>

      {/* Statistics */}
      {info?.statistics && (
        <Card className="p-4 sm:p-6 border-border">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 sm:gap-0 mb-4">
            <h3 className="text-lg sm:text-xl font-semibold">Statistics</h3>
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
        <div className="relative rounded-lg overflow-hidden bg-secondary min-h-[400px] flex items-center justify-center">
          {imageLoading && !imageError && (
            <div className="text-center p-8">
              <div className="w-12 h-12 border-4 border-primary border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
              <p className="text-muted-foreground">Loading visualization...</p>
            </div>
          )}
          {imageError && (
            <div className="text-center p-8">
              <p className="text-muted-foreground">Visualization not available</p>
              <p className="text-xs text-muted-foreground mt-2">The graph image may still be generating</p>
            </div>
          )}
          {imageUrl && !imageError && (
            <img
              src={imageUrl}
              alt="Knowledge Graph"
              className="w-full"
              style={{ display: imageLoading ? 'none' : 'block' }}
              onLoad={() => {
                setImageLoading(false);
                console.log('Visualization loaded successfully');
              }}
              onError={(e) => {
                setImageLoading(false);
                setImageError(true);
                console.error('Failed to load visualization');
              }}
            />
          )}
          {!imageLoading && !imageError && (
            <div className="absolute bottom-4 right-4 flex gap-2">
              <Button
                size="sm"
                onClick={() => window.open(imageUrl, '_blank')}
              >
                View Full Size
              </Button>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
};
