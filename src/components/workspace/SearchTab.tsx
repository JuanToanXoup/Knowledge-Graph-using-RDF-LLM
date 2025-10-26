import { useState } from 'react';
import { Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card } from '@/components/ui/card';
import { Slider } from '@/components/ui/slider';

interface SearchResult {
  text: string;
  score: number;
}

interface SearchTabProps {
  graphId: string;
}

export const SearchTab = ({ graphId }: SearchTabProps) => {
  const [query, setQuery] = useState('');
  const [topK, setTopK] = useState(5);
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);

  const handleSearch = async () => {
    setLoading(true);
    try {
      const response = await fetch('http://localhost:8000/semantic_search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ graph_id: graphId, query, top_k: topK }),
      });
      const data = await response.json();
      setResults(data.results || []);
    } catch (error) {
      console.error('Search failed:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-3xl font-bold mb-2">Semantic Search</h2>
        <p className="text-muted-foreground">
          Find relevant information using natural language queries
        </p>
      </div>

      {/* Search Interface */}
      <Card className="p-6 border-border">
        <div className="space-y-6">
          <div className="flex gap-4">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Enter your search query..."
                className="pl-10 h-12 border-border focus:border-primary"
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <Button
              onClick={handleSearch}
              disabled={!query || loading}
              className="h-12 px-8 shadow-glow-primary hover:shadow-glow-hover"
            >
              {loading ? 'Searching...' : 'Search'}
            </Button>
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">Results: {topK}</label>
              <span className="text-xs text-muted-foreground">1-20</span>
            </div>
            <Slider
              value={[topK]}
              onValueChange={(value) => setTopK(value[0])}
              min={1}
              max={20}
              step={1}
              className="w-full"
            />
          </div>
        </div>
      </Card>

      {/* Results */}
      {results.length > 0 && (
        <div className="space-y-4">
          <h3 className="text-xl font-semibold">Results ({results.length})</h3>
          <div className="space-y-4">
            {results.map((result, index) => (
              <Card
                key={index}
                className="p-6 border-border hover:border-primary/50 transition-all animate-fade-in"
              >
                <div className="flex items-start gap-4">
                  <div className="flex-1">
                    <p className="leading-relaxed">{result.text}</p>
                  </div>
                  <div className="px-3 py-1 rounded-lg bg-primary/10 text-primary text-sm font-medium whitespace-nowrap">
                    {(result.score * 100).toFixed(1)}%
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
