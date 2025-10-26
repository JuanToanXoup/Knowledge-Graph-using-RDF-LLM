import { ParticleBackground } from '@/components/ParticleBackground';
import { Header } from '@/components/Header';
import { HeroSection } from '@/components/landing/HeroSection';
import { FeaturesSection } from '@/components/landing/FeaturesSection';
import { HowItWorksSection } from '@/components/landing/HowItWorksSection';
import { ContactSection } from '@/components/landing/ContactSection';
import { Footer } from '@/components/Footer';

const Landing = () => {
  return (
    <div className="min-h-screen">
      <ParticleBackground />
      <Header showNav={true} />
      <HeroSection />
      <FeaturesSection />
      <HowItWorksSection />
      <ContactSection />
      <Footer />
    </div>
  );
};

export default Landing;
