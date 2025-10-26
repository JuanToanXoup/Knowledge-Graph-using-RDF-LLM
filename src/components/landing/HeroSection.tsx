import { useState } from 'react';
import { Upload, Zap, Lock } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useNavigate } from 'react-router-dom';
import { useToast } from '@/hooks/use-toast';
import heroGraph from '@/assets/hero-graph.jpg';

export const HeroSection = () => {
  const [file, setFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const navigate = useNavigate();
  const { toast } = useToast();

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const droppedFile = e.dataTransfer.files[0];
    if (droppedFile) setFile(droppedFile);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files?.[0]) setFile(e.target.files[0]);
  };

  const handleCreateGraph = async () => {
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);

    try {
      const response = await fetch('http://localhost:8000/upload', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) throw new Error('Upload failed');

      const data = await response.json();
      toast({
        title: 'Success!',
        description: `Graph created with ${data.entities_count} entities`,
      });
      navigate(`/workspace/${data.graph_id}`);
    } catch (error) {
      toast({
        title: 'Error',
        description: 'Failed to create knowledge graph. Make sure the API is running.',
        variant: 'destructive',
      });
    }
  };

  return (
    <section id="home" className="min-h-screen flex items-center justify-center px-4 sm:px-6 pt-24 sm:pt-32">
      <div className="max-w-7xl w-full grid md:grid-cols-[1.2fr,0.8fr] gap-8 md:gap-12 items-center">
        {/* Left Content */}
        <div className="space-y-6">
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-primary/10 border border-primary/20">
            <Zap className="w-4 h-4 text-primary" />
            <span className="text-sm text-primary">Powered by Advanced AI</span>
          </div>

          <h1 className="text-3xl sm:text-4xl md:text-5xl lg:text-6xl font-bold leading-tight">
            From Data Points to{' '}
            <span className="bg-gradient-ember bg-clip-text text-transparent">
              Knowledge Graphs
            </span>
          </h1>

          <p className="text-lg sm:text-xl text-muted-foreground max-w-2xl">
            Build, visualize, and reason through your own intelligent knowledge graph
          </p>

          <div className="flex flex-wrap gap-4">
            <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-card border border-border">
              <Zap className="w-5 h-5 text-primary" />
              <span className="text-sm">Lightning Fast</span>
            </div>
            <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-card border border-border">
              <Lock className="w-5 h-5 text-primary" />
              <span className="text-sm">Secure & Private</span>
            </div>
          </div>
        </div>

        {/* Right Content - Upload */}
        <div className="space-y-4">
          {!file ? (
            <div
              onDrop={handleDrop}
              onDragOver={(e) => {
                e.preventDefault();
                setIsDragging(true);
              }}
              onDragLeave={() => setIsDragging(false)}
              className={`relative border-2 border-dashed rounded-2xl p-8 sm:p-12 text-center transition-all cursor-pointer ${
                isDragging
                  ? 'border-primary bg-primary/5'
                  : 'border-primary/30 hover:border-primary/50 hover:bg-primary/5'
              }`}
              onClick={() => document.getElementById('file-input')?.click()}
            >
              <Upload className="w-12 h-12 sm:w-16 sm:h-16 text-primary mx-auto mb-4" />
              <h3 className="text-lg sm:text-xl font-semibold mb-2">Drag & drop your PDF here</h3>
              <p className="text-muted-foreground mb-4">or click to browse</p>
              <p className="text-sm text-muted-foreground">
                PDF, TXT, DOCX supported
              </p>
              <input
                id="file-input"
                type="file"
                accept=".pdf,.txt,.docx"
                onChange={handleFileChange}
                className="hidden"
              />
            </div>
          ) : (
            <div className="space-y-4">
              <div className="p-6 rounded-2xl bg-card border border-border">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <p className="font-semibold">{file.name}</p>
                    <p className="text-sm text-muted-foreground">
                      {(file.size / 1024 / 1024).toFixed(2)} MB
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setFile(null)}
                  >
                    Remove
                  </Button>
                </div>
              </div>
              <Button
                onClick={handleCreateGraph}
                className="w-full h-14 text-lg shadow-glow-primary hover:shadow-glow-hover"
              >
                Create Knowledge Graph
              </Button>
            </div>
          )}

          {/* Decorative Image */}
          <div className="relative rounded-2xl overflow-hidden border border-primary/20">
            <img
              src={heroGraph}
              alt="Knowledge Graph Visualization"
              className="w-full h-48 object-cover opacity-60"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-background to-transparent" />
          </div>
        </div>
      </div>
    </section>
  );
};
