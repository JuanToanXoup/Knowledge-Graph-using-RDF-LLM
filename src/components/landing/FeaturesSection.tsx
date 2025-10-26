import { Search, Link2, MessageSquare } from 'lucide-react';

const features = [
  {
    icon: Search,
    title: 'Smart Entity Extraction',
    description:
      'Automatically identify people, organizations, locations, and concepts from your documents',
  },
  {
    icon: Link2,
    title: 'Relationship Mapping',
    description:
      'Discover hidden connections and relationships between entities using advanced NLP',
  },
  {
    icon: MessageSquare,
    title: 'Intelligent Q&A',
    description:
      "Ask questions and get instant answers powered by your document's knowledge graph",
  },
];

export const FeaturesSection = () => {
  return (
    <section id="features" className="py-24 px-6">
      <div className="max-w-7xl mx-auto">
        <h2 className="text-4xl font-bold text-center mb-16">
          Powerful Features
        </h2>

        <div className="grid md:grid-cols-3 gap-8">
          {features.map((feature) => {
            const Icon = feature.icon;
            return (
              <div
                key={feature.title}
                className="group p-8 rounded-2xl bg-card border border-border hover:border-primary/50 transition-all hover:-translate-y-2 duration-300"
              >
                <div className="w-14 h-14 rounded-xl bg-primary/10 flex items-center justify-center mb-6 group-hover:shadow-glow-primary transition-all">
                  <Icon className="w-7 h-7 text-primary" />
                </div>
                <h3 className="text-xl font-semibold mb-3">{feature.title}</h3>
                <p className="text-muted-foreground leading-relaxed">
                  {feature.description}
                </p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
