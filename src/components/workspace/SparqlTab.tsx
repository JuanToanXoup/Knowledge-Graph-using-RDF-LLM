import { useState } from 'react';
import { Play, Trash2, Download } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Textarea } from '@/components/ui/textarea';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';

const exampleQueries = [
  {
    name: 'Get All Entities',
    query: 'SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object } LIMIT 10',
  },
  {
    name: 'Entity Count',
    query: 'SELECT (COUNT(DISTINCT ?entity) as ?count) WHERE { ?entity ?p ?o }',
  },
  {
    name: 'Find Relationships',
    query: 'SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object . FILTER(?predicate != rdf:type) } LIMIT 20',
  },
];

interface SparqlTabProps {
  graphId: string;
}

export const SparqlTab = ({ graphId }: SparqlTabProps) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [executionTime, setExecutionTime] = useState<number | null>(null);

  const handleExecute = async () => {
    if (!query.trim()) return;

    setLoading(true);
    setError('');
    const startTime = Date.now();

    try {
      const response = await fetch('http://localhost:8000/sparql_query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ graph_id: graphId, query }),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.detail || 'Query execution failed');
      }

      const data = await response.json();
      setResults(data.results || []);
      setExecutionTime(Date.now() - startTime);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const downloadResults = (format: 'csv' | 'json') => {
    if (results.length === 0) return;

    let content: string;
    let filename: string;
    let type: string;

    if (format === 'csv') {
      const headers = Object.keys(results[0]);
      const csv = [
        headers.join(','),
        ...results.map((row) => headers.map((h) => row[h]).join(',')),
      ].join('\n');
      content = csv;
      filename = 'query-results.csv';
      type = 'text/csv';
    } else {
      content = JSON.stringify(results, null, 2);
      filename = 'query-results.json';
      type = 'application/json';
    }

    const blob = new Blob([content], { type });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
  };

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-3xl font-bold mb-2">SPARQL Query</h2>
        <p className="text-muted-foreground">
          Execute SPARQL queries on your knowledge graph
        </p>
      </div>

      <div className="grid lg:grid-cols-[1fr,300px] gap-6">
        {/* Editor Section */}
        <div className="space-y-6">
          <Card className="p-6 border-border">
            <h3 className="text-lg font-semibold mb-4">Query Editor</h3>
            <Textarea
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Enter your SPARQL query..."
              className="min-h-[300px] font-mono text-sm border-border focus:border-primary"
            />
            <div className="flex items-center gap-4 mt-4">
              <Button
                onClick={handleExecute}
                disabled={!query || loading}
                className="gap-2 shadow-glow-primary hover:shadow-glow-hover"
              >
                <Play className="w-4 h-4" />
                {loading ? 'Executing...' : 'Execute Query'}
              </Button>
              <Button
                variant="outline"
                onClick={() => setQuery('')}
                disabled={!query}
              >
                <Trash2 className="w-4 h-4 mr-2" />
                Clear
              </Button>
              {executionTime !== null && (
                <span className="text-sm text-muted-foreground ml-auto">
                  Executed in {executionTime}ms
                </span>
              )}
            </div>
          </Card>

          {/* Error Display */}
          {error && (
            <Card className="p-6 border-destructive bg-destructive/5">
              <h3 className="text-lg font-semibold text-destructive mb-2">
                Query Error
              </h3>
              <p className="text-sm">{error}</p>
            </Card>
          )}

          {/* Results */}
          {results.length > 0 && (
            <Card className="border-border">
              <div className="p-6 border-b border-border flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-semibold">
                    Results ({results.length})
                  </h3>
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => downloadResults('csv')}
                    className="gap-2"
                  >
                    <Download className="w-4 h-4" />
                    CSV
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => downloadResults('json')}
                    className="gap-2"
                  >
                    <Download className="w-4 h-4" />
                    JSON
                  </Button>
                </div>
              </div>
              <div className="overflow-auto max-h-[500px]">
                <Table>
                  <TableHeader>
                    <TableRow className="hover:bg-transparent border-border">
                      {Object.keys(results[0]).map((key) => (
                        <TableHead key={key} className="font-semibold">
                          {key}
                        </TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {results.map((row, i) => (
                      <TableRow key={i} className="border-border">
                        {Object.values(row).map((value: any, j) => (
                          <TableCell key={j}>
                            {typeof value === 'object'
                              ? JSON.stringify(value)
                              : String(value)}
                          </TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </Card>
          )}
        </div>

        {/* Examples Sidebar */}
        <div className="space-y-4">
          <Card className="p-6 border-border">
            <h3 className="text-lg font-semibold mb-4">Example Queries</h3>
            <div className="space-y-3">
              {exampleQueries.map((example) => (
                <button
                  key={example.name}
                  onClick={() => setQuery(example.query)}
                  className="w-full text-left p-3 rounded-lg bg-muted hover:bg-primary/10 transition-colors"
                >
                  <p className="text-sm font-medium mb-1">{example.name}</p>
                  <p className="text-xs text-muted-foreground line-clamp-2">
                    {example.query}
                  </p>
                </button>
              ))}
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};
