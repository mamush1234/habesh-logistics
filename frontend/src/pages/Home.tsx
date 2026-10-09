import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PublicApi } from '../api/endpoints';
import { Package, MapPin, Phone, PlaneTakeoff, Shield } from 'lucide-react';

export function Home() {
  const info = useQuery({ queryKey: ['public-info'], queryFn: PublicApi.info });

  return (
    <div className="space-y-14">
      <section className="grid md:grid-cols-2 gap-10 items-center">
        <div className="space-y-6">
          <span className="badge badge-amber"><PlaneTakeoff className="w-3.5 h-3.5" /> IAD → ADD · Community-run</span>
          <h1 className="text-4xl md:text-6xl font-bold leading-tight">
            Send packages to <span className="text-brand-600">Ethiopia</span>, through your community.
          </h1>
          <p className="text-ink-500 text-lg max-w-xl">
            One tracking number per package. We consolidate shipments with trusted travelers heading to Addis Ababa
            and hand everything to a receiving agent on the ground.
          </p>
          <div className="flex flex-wrap gap-3">
            <Link to="/send" className="btn btn-brand" data-testid="cta-send">
              <Package className="w-4 h-4" /> Send a Package
            </Link>
            <Link to="/track" className="btn btn-ghost" data-testid="cta-track">
              <MapPin className="w-4 h-4" /> Track my Package
            </Link>
            <a href="tel:+15550100" className="btn btn-ghost">
              <Phone className="w-4 h-4" /> Contact
            </a>
          </div>
        </div>

        <div className="card bg-ink-900 text-white relative overflow-hidden">
          <div className="absolute -right-10 -top-10 w-40 h-40 rounded-full bg-brand-600/30 blur-3xl"></div>
          <h3 className="text-sm uppercase tracking-widest text-brand-500 font-bold">Alexandria Drop-off</h3>
          <p className="mt-2 text-2xl font-semibold">{info.data?.alexandriaAddress || '1234 King St, Alexandria, VA'}</p>
          <p className="text-ink-300 mt-1">{info.data?.alexandriaHours || 'Mon–Sat 9AM–7PM'}</p>
          <div className="mt-6 grid grid-cols-2 gap-4 text-sm">
            <div className="rounded-xl bg-white/5 p-3">
              <div className="text-ink-300 text-xs">Rate per kg</div>
              <div className="text-xl font-bold">${info.data?.defaultRatePerKg ?? '10.00'}</div>
            </div>
            <div className="rounded-xl bg-white/5 p-3">
              <div className="text-ink-300 text-xs">Pickup fee (optional)</div>
              <div className="text-xl font-bold">${info.data?.defaultPickupFee ?? '15.00'}</div>
            </div>
          </div>
        </div>
      </section>

      <section className="grid md:grid-cols-4 gap-4">
        {[
          { icon: Package, title: 'Create', body: 'Enter weight, receiver and destination.' },
          { icon: MapPin, title: 'Drop-off / Pickup', body: 'Bring to Alexandria or request pickup.' },
          { icon: PlaneTakeoff, title: 'Consolidate', body: 'We pack into a traveler’s luggage.' },
          { icon: Shield, title: 'Deliver', body: 'Our Ethiopia agent hands over safely.' },
        ].map((c, i) => (
          <div className="card" key={i}>
            <c.icon className="w-6 h-6 text-brand-600" />
            <h3 className="mt-3 font-semibold text-lg">{c.title}</h3>
            <p className="text-ink-500 text-sm mt-1">{c.body}</p>
          </div>
        ))}
      </section>
    </div>
  );
}
