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
    <section id="how-it-works" className="py-16 sm:py-24 px-4 sm:px-6">
      <div className="max-w-7xl mx-auto">
        <h2 className="text-3xl sm:text-4xl font-bold text-center mb-12 sm:mb-16">
          How It Works
        </h2>

        <div className="relative">
          {/* Connection Line */}
          <div className="hidden md:block absolute top-16 sm:top-20 left-0 right-0 h-0.5 bg-gradient-to-r from-transparent via-primary to-transparent" />

          <div className="grid sm:grid-cols-2 md:grid-cols-3 gap-8 sm:gap-12 relative">
            {steps.map((step) => {
              const Icon = step.icon;
              return (
                <div key={step.number} className="text-center space-y-3 sm:space-y-4">
                  <div className="relative inline-block">
                    <div className="w-32 h-32 sm:w-40 sm:h-40 rounded-full bg-card border-2 border-primary flex items-center justify-center mx-auto relative z-10">
                      <Icon className="w-12 h-12 sm:w-16 sm:h-16 text-primary" />
                    </div>
                    <div className="absolute -top-1 -right-1 sm:-top-2 sm:-right-2 w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-primary text-primary-foreground flex items-center justify-center font-bold text-base sm:text-lg shadow-glow-primary">
                      {step.number}
                    </div>
                  </div>
                  <h3 className="text-xl sm:text-2xl font-bold">{step.title}</h3>
                  <p className="text-sm sm:text-base text-muted-foreground">{step.description}</p>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
};
