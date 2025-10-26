import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Mail } from 'lucide-react';

export const ContactSection = () => {
  const [email, setEmail] = useState('');

  return (
    <section className="py-24 px-6">
      <div className="max-w-4xl mx-auto text-center space-y-8">
        <h2 className="text-4xl md:text-5xl font-bold">
          Get Started Today
        </h2>
        <p className="text-xl text-muted-foreground">
          Transform your documents into interactive knowledge graphs
        </p>

        <div className="flex flex-col sm:flex-row gap-4 max-w-xl mx-auto">
          <Input
            type="email"
            placeholder="Enter your email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="h-12 bg-card border-border focus:border-primary"
          />
          <Button className="h-12 px-8 shadow-glow-primary hover:shadow-glow-hover whitespace-nowrap">
            Get Early Access
          </Button>
        </div>

        <div className="flex items-center justify-center gap-2">
          <span className="text-muted-foreground">or</span>
        </div>

        <div>
          <Button variant="outline" size="lg" className="gap-2">
            <Mail className="w-4 h-4" />
            Contact Us
          </Button>
          <p className="text-sm text-muted-foreground mt-4">
            madhav@knowledgegraph.ai
          </p>
        </div>
      </div>
    </section>
  );
};
