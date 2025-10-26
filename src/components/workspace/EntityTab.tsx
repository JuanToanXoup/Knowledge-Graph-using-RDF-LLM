import { useState } from 'react';
import { Search, Download } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card } from '@/components/ui/card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';

interface Relation {
  subject: string;
  predicate: string;
  object: string;
}

interface EntityTabProps {
  graphId: string;
}

export const EntityTab = ({ graphId }: EntityTabProps) => {
  const [entity, setEntity] = useState('');
  const [relations, setRelations] = useState<Relation[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedEntity, setSelectedEntity] = useState('');

  const handleSearch = async () => {
    if (!entity.trim()) return;

    setLoading(true);
    setSelectedEntity(entity);
    try {
      const response = await fetch('http://localhost:8000/entity_relations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ graph_id: graphId, entity_name: entity }),
      });
      const data = await response.json();
      setRelations(data.relations || []);
    } catch (error) {
      console.error('Failed to fetch relations:', error);
    } finally {
      setLoading(false);
    }
  };

  const exportCSV = () => {
    const csv = [
      ['Subject', 'Predicate', 'Object'],
      ...relations.map((r) => [r.subject, r.predicate, r.object]),
    ]
      .map((row) => row.join(','))
      .join('\n');

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${selectedEntity}-relations.csv`;
    a.click();
  };

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-3xl font-bold mb-2">Entity Explorer</h2>
        <p className="text-muted-foreground">
          Explore entities and their relationships
        </p>
      </div>

      {/* Search */}
      <Card className="p-6 border-border">
        <div className="flex gap-4">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
            <Input
              value={entity}
              onChange={(e) => setEntity(e.target.value)}
              placeholder="Enter entity name..."
              className="pl-10 h-12 border-border focus:border-primary"
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <Button
            onClick={handleSearch}
            disabled={!entity || loading}
            className="h-12 px-8 shadow-glow-primary hover:shadow-glow-hover"
          >
            {loading ? 'Loading...' : 'Explore'}
          </Button>
        </div>
      </Card>

      {/* Results */}
      {selectedEntity && (
        <div className="space-y-6">
          <Card className="p-6 border-border border-l-4 border-l-primary">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-2xl font-bold mb-2">{selectedEntity}</h3>
                <p className="text-sm text-muted-foreground">
                  {relations.length} relations found
                </p>
              </div>
              <Button
                variant="outline"
                onClick={exportCSV}
                disabled={relations.length === 0}
                className="gap-2"
              >
                <Download className="w-4 h-4" />
                Export CSV
              </Button>
            </div>
          </Card>

          {relations.length > 0 && (
            <Card className="border-border overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent border-border">
                    <TableHead className="font-semibold">Subject</TableHead>
                    <TableHead className="font-semibold">Predicate</TableHead>
                    <TableHead className="font-semibold">Object</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {relations.map((relation, index) => (
                    <TableRow key={index} className="border-border">
                      <TableCell className="font-medium">
                        {relation.subject}
                      </TableCell>
                      <TableCell className="text-primary">
                        {relation.predicate}
                      </TableCell>
                      <TableCell>{relation.object}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Card>
          )}
        </div>
      )}
    </div>
  );
};
