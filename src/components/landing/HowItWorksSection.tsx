import { Upload, Cpu, Eye } from 'lucide-react';

const steps = [
  {
    icon: Upload,
    number: '01',
    title: 'Upload',
    description: 'Drop your document',
  },
  {
    icon: Cpu,
    number: '02',
    title: 'Process',
    description: 'AI extracts entities & relations',
  },
  {
    icon: Eye,
    number: '03',
    title: 'Explore',
    description: 'Query and visualize insights',
  },
];

export const HowItWorksSection = () => {
  return (
    <section id="how-it-works" className="py-24 px-6">
      <div className="max-w-7xl mx-auto">
        <h2 className="text-4xl font-bold text-center mb-16">
          How It Works
        </h2>

        <div className="relative">
          {/* Connection Line */}
          <div className="hidden md:block absolute top-20 left-0 right-0 h-0.5 bg-gradient-to-r from-transparent via-primary to-transparent" />

          <div className="grid md:grid-cols-3 gap-12 relative">
            {steps.map((step) => {
              const Icon = step.icon;
              return (
                <div key={step.number} className="text-center space-y-4">
                  <div className="relative inline-block">
                    <div className="w-40 h-40 rounded-full bg-card border-2 border-primary flex items-center justify-center mx-auto relative z-10">
                      <Icon className="w-16 h-16 text-primary" />
                    </div>
                    <div className="absolute -top-2 -right-2 w-12 h-12 rounded-full bg-primary text-primary-foreground flex items-center justify-center font-bold text-lg shadow-glow-primary">
                      {step.number}
                    </div>
                  </div>
                  <h3 className="text-2xl font-bold">{step.title}</h3>
                  <p className="text-muted-foreground">{step.description}</p>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
};
