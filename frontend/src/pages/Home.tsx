import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, CalendarDays, Check, Handshake, Heart, Luggage, MapPin, Package, Plane, ShieldCheck } from 'lucide-react';
import { PublicApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';

const steps = [
  { icon: Package, title: 'Post or find a package', description: 'Share what you need to send, or browse available packages.' },
  { icon: Handshake, title: 'Find and confirm a match', description: 'Connect with the right traveler or sender.' },
  { icon: MapPin, title: 'Track the shipment', description: 'Follow your package, all the way to delivery.' },
];

export function Home() {
  const { user } = useAuth();
  const traveler = user?.role === 'TRAVELER';
  const travel = useQuery({ queryKey: ['public-travel'], queryFn: PublicApi.travel, enabled: !traveler, refetchInterval: 60_000 });
  const info = useQuery({ queryKey: ['public-info'], queryFn: PublicApi.info });
  const sendTo = user && user.role !== 'SENDER' ? '/marketplace' : '/send';
  const travelingTo = !user ? '/register?role=TRAVELER'
    : user.role === 'TRAVELER' ? '/traveler'
    : user.role === 'SENDER' ? '/my-shipments' : '/marketplace';

  return <div className="landing-page" id="home">
    <section className="landing-hero" aria-labelledby="hero-title">
      <div className="hero-art" aria-hidden="true">
        <img src="/travel-community.svg" alt="" />
      </div>
      <div className="hero-content">
        <div className="hero-eyebrow"><span /> A little luggage. A lot of connection.</div>
        <h1 id="hero-title">Send Packages with<br /><span>Trusted Travelers</span></h1>
        <p className="hero-description">Connect with travelers heading to your destination.<br className="hidden sm:block" /> Request, match, and track — all in one place.</p>
        <div className="hero-actions">
          <Link to={sendTo} className="landing-button landing-button-primary" data-testid="cta-send">
            <Package size={25} /> {user && user.role !== 'SENDER' ? 'Browse Packages' : 'Send a Package'} <ArrowRight size={21} />
          </Link>
          <Link to={travelingTo} className="landing-button landing-button-outline" data-testid="cta-travel">
            <Plane size={25} /> {user?.role === 'SENDER' ? 'My Packages' : "I'm Traveling"} <ArrowRight size={21} />
          </Link>
        </div>
        <div className="hero-signoff">From anywhere <span>→</span> To Ethiopia <Heart size={24} /></div>
        <div className="hero-reassurance"><ShieldCheck size={16} /> Community connections. Clear tracking. Closer to home.</div>
      </div>
    </section>

    <div className="landing-panels">
      <section className="how-panel" id="how-it-works" aria-labelledby="how-title">
        <h2 id="how-title">How It Works</h2>
        <div className="heading-underline" />
        <div className="how-steps">
          {steps.map((step, index) => <div className="how-step" key={step.title}>
            <div className="step-illustration"><step.icon size={37} strokeWidth={1.6} /><span>{index + 1}</span></div>
            <div><h3>{step.title}</h3><p>{step.description}</p></div>
            {index < 2 && <ArrowRight className="step-arrow" size={22} />}
          </div>)}
        </div>
      </section>

      {traveler ? <section className="available-panel">
        <h2>Sender requests</h2>
        <p className="text-sm mt-3">Find packages to carry on your trip and send an offer.</p>
        <Link to="/marketplace" className="trip-link mt-3">Browse sender requests <ArrowRight size={15} /></Link>
        <Link to="/traveler" className="trip-link mt-3">My trips <ArrowRight size={15} /></Link>
      </section> : <section className="available-panel" id="available-trips" aria-labelledby="available-title">
        <div className="flex justify-between items-center gap-2">
          <h2 id="available-title">Available Trips</h2>
          <a href="#trips" className="text-sm font-semibold text-blue-700 inline-flex items-center gap-1">View all <ArrowRight size={15} /></a>
        </div>
        {travel.isPending && <p role="status" className="py-5 text-sm">Finding upcoming trips...</p>}
        {travel.isError && <p role="alert" className="py-4 text-sm">Could not load trips. <button className="underline" onClick={() => travel.refetch()}>Try again</button></p>}
        {travel.data?.length === 0 && <div className="py-4">
          <p className="font-semibold">The next connection starts with you.</p>
          <p className="text-sm mt-1 text-blue-900/70">No trips with available space right now. Check back soon.</p>
          <Link to={travelingTo} className="trip-link mt-3">Get started <ArrowRight size={15} /></Link>
        </div>}
        {travel.data?.slice(0, 1).map(trip => <div key={trip.id} className="mt-3">
          <div className="flex gap-3 items-center">
            <div className="trip-plane"><Plane size={34} strokeWidth={1.4} /></div>
            <div className="min-w-0"><h3 className="font-bold text-sm">{trip.origin} → {trip.destination}</h3>
              <div className="trip-meta">
                <span><CalendarDays size={14} /> {new Date(`${trip.departureDate}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}</span>
                <span><Luggage size={14} /> {trip.availableCapacityKg} kg available</span>
              </div>
            </div>
          </div>
          {(!user || user.role === 'SENDER') &&
            <Link to={`/send?travel=${encodeURIComponent(trip.id)}`} className="trip-link mt-3">Request space <ArrowRight size={15} /></Link>}
        </div>)}
      </section>}
    </div>

    <div className="landing-horizon" aria-hidden="true">
      <svg viewBox="0 0 1440 105" preserveAspectRatio="none">
        <path fill="#b7dcff" d="M0 70V40h12V28h9v12h12v30h15V50h20V32h8v18h12v20h24V45h12V15h10v30h12v25h20V55h30v15h25V35h18v35h30V45h12V20h8v25h12v25h30V48h25v22h20V30h12V5h7v25h12v40h20V50h25v20h35V35h18v35h15V45h22v25h25V25h12V8h5v17h12v45h30V50h25v20h25V36h18v34h25V45h24v25h30V28h15V0h6v28h15v42h28V46h25v24h35V33h20v37h25V50h25v20h24V40h20v30h30V20h12V8h6v12h12v50h25V48h25v22h35V35h18v35h30V50h30v20h30V30h15v40h25V45h20v25h50V35h20v35h60V50h35v20h150v35H0Z" />
        <path fill="#087441" d="M650 105C930 55 1120 5 1440 18v87Z" />
        <path fill="#ffda31" d="M760 105c240-43 440-75 680-60v60Z" />
        <path fill="#e94737" d="M970 105c190-34 320-40 470-32v32Z" />
        <path fill="#075dc6" d="M0 90c185-20 340 0 475 15H0Z" />
        <circle cx="1315" cy="69" r="26" fill="#0765d4" />
        <path d="m1315 48 5 15 16-1-13 9 5 16-13-10-13 10 5-16-13-9 16 1Z" fill="none" stroke="#ffdc37" strokeWidth="1.5" />
      </svg>
    </div>

    <div className="landing-details">
      {!traveler && <section id="trips" className="space-y-5 scroll-mt-24">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div><p className="section-kicker">FIND YOUR CONNECTION</p><h2 className="text-3xl font-bold mt-2">A journey already heading your way.</h2></div>
          {user?.role === 'SENDER' && <Link className="trip-link !w-auto px-5" to="/my-shipments">My packages <ArrowRight size={16} /></Link>}
        </div>
        <>
          {travel.isError && <p role="alert">Trips are unavailable. <button className="underline" onClick={() => travel.refetch()}>Retry</button></p>}
          {travel.isPending && <p role="status">Loading trips...</p>}
          {travel.data?.length === 0 && <p className="text-blue-900/70">New trips will appear here when space becomes available.</p>}
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {travel.data?.map(trip => <article key={trip.id} className="public-trip-card">
              <Plane className="text-blue-600 mb-4" />
              <h3 className="font-bold text-lg">{trip.origin} → {trip.destination}</h3>
              <p className="text-sm mt-2 text-blue-900/70">{new Date(`${trip.departureDate}T00:00:00`).toLocaleDateString()} · {trip.availableCapacityKg} kg available</p>
              <Link className="trip-link mt-5" to={user && user.role !== 'SENDER' ? '/marketplace' : `/send?travel=${encodeURIComponent(trip.id)}`}>{user && user.role !== 'SENDER' ? 'Open marketplace' : 'Request space'} <ArrowRight size={16} /></Link>
            </article>)}
          </div>
        </>
      </section>}

      <section id="about" className="about-panel scroll-mt-24">
        <div><p className="section-kicker">MORE THAN A DELIVERY</p><h2 className="text-3xl font-bold mt-2">A community that brings<br />home a little closer.</h2></div>
        <div className="space-y-4 text-blue-950/75">
          <p>From everyday essentials to thoughtful gifts, HabeshaList connects people sending packages to Ethiopia with travelers who have room to carry them.</p>
          <p>Post a request, compare traveler offers, and follow delivery progress in My packages. Escrow is currently a clearly labeled demo, with no real payments.</p>
          <div className="flex flex-wrap gap-4 text-sm font-semibold text-blue-800"><span className="inline-flex gap-1 items-center"><Check size={17} /> Clear package details</span><span className="inline-flex gap-1 items-center"><Check size={17} /> Private contact information</span></div>
        </div>
      </section>

      <section id="help" className="help-panel scroll-mt-24">
        <div><p className="section-kicker">HERE TO HELP</p><h2 className="text-2xl font-bold mt-2">Your package. Your peace of mind.</h2>
          <p className="text-sm mt-2 text-blue-950/70">Find new requests and delivery progress in My packages. Existing operational shipments still use tracking numbers.</p>
          <Link to="/track" className="landing-button landing-button-primary mt-5 !text-sm !py-3">Track a package <ArrowRight size={17} /></Link>
        </div>
        <div className="text-sm space-y-2">
          <h3 className="font-bold text-lg">Alexandria drop-off</h3>
          {info.isPending && <p role="status">Loading drop-off details...</p>}
          {info.isError && <p role="alert">Could not load drop-off details. <button className="underline" onClick={() => info.refetch()}>Retry</button></p>}
          {info.data && <><p>{info.data.alexandriaAddress}</p><p>{info.data.alexandriaHours}</p></>}
          <p className="text-blue-950/70">Need an agent account? Contact your service administrator.</p>
        </div>
      </section>
    </div>
  </div>;
}
