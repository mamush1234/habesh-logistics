import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Package, Plane, Users, ShieldCheck, CreditCard, MapPin, ArrowRight, Search } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { PublicApi } from '../api/endpoints';
import { MarketplaceApi } from '../api/marketplace';
import { useAuth } from '../auth/AuthContext';
import { RequestThumbnail } from '../components/RequestThumbnail';
import { filterTrips, includesCity, initialTripFilters, TripListFilters } from '../components/TripListFilters';

const steps = [
  { title: 'Post or search', description: 'Senders describe the parcel. Travelers share their route and spare luggage space.' },
  { title: 'Agree on a price', description: 'Agents send offers, and the sender picks the one that fits. Travelers currently view approved requests only.' },
  { title: 'Track and confirm', description: 'Follow delivery updates. At arrival, share your private delivery code with the receiver for the agent or admin to confirm delivery.' },
];
const trust = [
  { title: 'Trusted Travelers', icon: ShieldCheck, description: 'Check administrator-assigned verification and completed-delivery ratings before choosing an offer.' },
  { title: 'Test-mode Payments', icon: CreditCard, description: 'Demo escrow and Stripe test payments only. No real money is collected or transferred.' },
  { title: 'Track Your Shipment', icon: MapPin, description: 'Follow your package with timestamped delivery updates linked to your account.' },
  { title: 'Ethiopian Community', icon: Users, description: 'Connect senders in the USA with travelers going to Ethiopia. Follow airline and customs rules.' },
];
const formatDate = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

export function Home() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const traveler = user?.role === 'TRAVELER' || user?.role === 'AGENT';
  const [selectedMode, setMode] = useState<'send' | 'travel' | null>(null);
  const mode = selectedMode ?? (traveler ? 'travel' : 'send');
  const showingRequests = mode === 'travel';
  const canReviewRequests = traveler || user?.role === 'ADMIN';
  const [form, setForm] = useState({ from: '', to: '', date: '' });
  const [search, setSearch] = useState(form);
  const [tripFilters, setTripFilters] = useState(initialTripFilters);
  const results = useRef<HTMLElement>(null);
  const travel = useQuery({ queryKey: ['public-travel'], queryFn: PublicApi.travel, enabled: !showingRequests, refetchInterval: 60_000 });
  const requests = useQuery({ queryKey: ['public-requests'], queryFn: PublicApi.requests, enabled: showingRequests, refetchInterval: 15_000 });
  const internalRequests = user?.role === 'AGENT' || user?.role === 'ADMIN';
  const accountRequests = useQuery({ queryKey: ['marketplace', 'requests', user?.id], queryFn: MarketplaceApi.requests,
    enabled: showingRequests && internalRequests, refetchInterval: 15_000 });
  const requestResults = internalRequests ? accountRequests : requests;
  const postTripTo = !user ? '/register?role=TRAVELER&next=%2Ftraveler'
    : traveler ? '/traveler' : user.role === 'SENDER' ? '/my-shipments' : '/marketplace';
  const actions = [
    { title: 'Send a Package', icon: Package, description: 'Describe your package and find space on an upcoming trip.',
      to: !user ? '/register?role=SENDER&next=%2Fsend' : user.role === 'SENDER' ? '/send' : '/browse-travelers', label: 'Get started' },
    { title: 'Post a Trip', icon: Plane, description: 'Share your route and available luggage space with the community.',
      to: postTripTo, label: user?.role === 'SENDER' ? 'View my packages' : user && !traveler ? 'Open marketplace' : 'Post your trip' },
    { title: 'Become an Agent', icon: Users, description: 'Help your customers send packages and coordinate trips.',
      to: user?.role === 'AGENT' ? '/agent' : '/register?role=AGENT', label: user?.role === 'AGENT' ? 'Open agent portal' : 'Learn about agent access' },
  ];
  const trips = travel.data && filterTrips(travel.data.filter(t => includesCity(t.origin, search.from) && includesCity(t.destination, search.to)
    && (!search.date || t.departureDate === search.date)), tripFilters);
  const packages = (internalRequests ? accountRequests.data?.filter(r => r.status === 'REQUESTED') : requests.data)?.filter(r => includesCity(r.pickupCity, search.from)
    && includesCity(r.destinationCity, search.to) && (!search.date || (r.availableFrom <= search.date && r.deadline >= search.date)));
  function selectMode(next: 'send' | 'travel') {
    setMode(next);
    setForm({ ...form, date: '' });
    setSearch({ ...search, date: '' });
  }

  return <div className="landing-page" id="home">
    <section className="landing-hero" aria-labelledby="hero-title">
      <div className="hero-content">
        <p className="hero-eyebrow">A closer connection to home</p>
        <h1 id="hero-title">Send Packages<br />with Trusted Travelers</h1>
        <p className="hero-description">Connect with travelers to send your packages<br className="hidden sm:block" /> from the USA to Ethiopia and beyond.</p>
      </div>
      <form className="landing-search" aria-label="Find a delivery connection" onSubmit={event => {
        event.preventDefault();
        if (!showingRequests) {
          const params = new URLSearchParams();
          if (form.from.trim()) params.set('origin', form.from.trim());
          if (form.to.trim()) params.set('destination', form.to.trim());
          if (form.date) { params.set('from', form.date); params.set('to', form.date); }
          params.set('sort', 'departure');
          navigate(`/browse-travelers?${params}`);
          return;
        }
        setSearch(form);
        results.current?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
      }}>
        <div className="search-modes" role="group" aria-label="Delivery type">
          <button type="button" aria-pressed={mode === 'send'} className={mode === 'send' ? 'selected' : ''} onClick={() => selectMode('send')}>I am a sender</button>
          <button type="button" aria-pressed={mode === 'travel'} className={mode === 'travel' ? 'selected' : ''} onClick={() => selectMode('travel')}>I am a traveler</button>
        </div>
        <div className="search-fields">
          <label>From<input maxLength={100} placeholder="Washington DC" value={form.from} onChange={e => setForm({ ...form, from: e.target.value })} /></label>
          <label>To<input maxLength={100} placeholder="Addis Ababa" value={form.to} onChange={e => setForm({ ...form, to: e.target.value })} /></label>
          <label>Departure date<input type="date" value={form.date} onChange={e => setForm({ ...form, date: e.target.value })} /></label>
          <button className="home-button home-button-green" type="submit"><Search className="h-4 w-4" aria-hidden="true" />{showingRequests ? 'Find packages' : 'Search travelers'}</button>
        </div>
        <p className="search-note">{showingRequests ? 'Browse open sender requests. Public visitors and travelers see only admin-approved requests. Traveler access is read-only; agents can still make offers. The departure date must fall within the package availability window.' : 'Search opens Browse Travelers with your route and departure date, sorted by soonest departure. Preview the latest posts below. Confirm arrival before the delivery deadline when matching.'}</p>
      </form>
      <p className="hero-photo-credit">Ethiopian Airlines Boeing 767 taking off at Addis Ababa Bole Airport.
        {' '}Photo &copy; Raimund Stehmann, <a href="https://commons.wikimedia.org/wiki/File:Ethiopian_Airlines_Boeing_767-300ER_taking_off_at_Bole_International_Airport.jpg" target="_blank" rel="noreferrer">Wikimedia Commons</a>.
        {' '}<a href="/GFDL-1.3.txt" target="_blank" rel="noreferrer">GFDL 1.2 or later</a>.
        {' '}<a href="/ethiopian-bole-takeoff.jpg" target="_blank" rel="noreferrer">Original photo</a>; display cropped with a text overlay. No airline affiliation or endorsement.</p>
    </section>

    <section className="home-section home-actions" aria-labelledby="actions-title">
      <h2 id="actions-title" className="sr-only">How can we help you?</h2>
      <div className="home-action-grid">{actions.map(action => <article className="home-trip-card" key={action.title}>
        <span className={`home-action-icon ${action.title === 'Send a Package' ? 'parcel' : action.title === 'Post a Trip' ? 'flight' : 'community'}`}><action.icon aria-hidden="true" /></span>
        <h3>{action.title}</h3><p>{action.description}</p>
        {action.title === 'Become an Agent' && user?.role !== 'AGENT' && <p>Agent access requires administrator approval.</p>}
        <Link className="home-button home-button-green" to={action.to}>{action.label}<ArrowRight className="h-4 w-4" aria-hidden="true" /></Link>
      </article>)}</div>
      <div className="home-trust-strip" aria-label="Community safeguards">{trust.map(item => <a href="#safety" key={item.title}>
        <item.icon aria-hidden="true" /><span>{item.title}</span>
      </a>)}</div>
    </section>

    <section className="home-section" id="trips" ref={results} aria-labelledby="trips-title">
      <div className="home-section-heading">
        <h2 id="trips-title">{showingRequests ? 'Sender requests' : 'Traveler / agent trips'}</h2>
        <Link to={postTripTo} className="home-text-link">{user?.role === 'SENDER' ? 'My packages' : user && !traveler ? 'Open marketplace' : 'Post your trip'}</Link>
      </div>
      {showingRequests ? <>
        {requestResults.isPending && <p role="status">Finding sender requests...</p>}
        {requestResults.isError && <p role="alert">Could not load requests. <button className="home-text-link" onClick={() => requestResults.refetch()}>Try again</button></p>}
        {packages?.length === 0 && <p className="home-empty">{internalRequests ? 'No open requests match this search.' : 'No admin-approved open requests match this search.'} Try another city or date, or <Link className="home-text-link" to="/marketplace">browse sender requests</Link>.</p>}
        <div className="home-request-list">{packages?.map(r => <article key={r.id} className="home-trip-card home-request-card">
          <RequestThumbnail id={r.id} hasPhoto={r.hasProductPhoto} publicPreview={!internalRequests} />
          <div className="home-request-content">
            <h3>{r.pickupCity} to {r.destinationCity}</h3>
            <p>Drop-off {formatDate(r.availableFrom)} &middot; Deliver by {formatDate(r.deadline)}</p>
            <div className="home-trip-details"><span>{r.weightKg} kg &middot; {r.itemType.toLowerCase()}</span><strong>${r.offeredPrice.toFixed(2)}</strong></div>
            <Link className="home-button home-button-outline" to={canReviewRequests ? `/marketplace/requests/${r.id}` : `/login?next=${encodeURIComponent(`/marketplace/requests/${r.id}`)}`}>{canReviewRequests ? 'Review request' : 'Sign in as a traveler to review'}</Link>
          </div>
        </article>)}</div>
      </> : <>
        <TripListFilters value={tripFilters} onChange={setTripFilters} />
        {travel.isPending && <p role="status">Finding upcoming trips...</p>}
        {travel.isError && <p role="alert">Could not load trips. <button className="home-text-link" onClick={() => travel.refetch()}>Try again</button></p>}
        {trips?.length === 0 && <p className="home-empty">No upcoming trips match this search. Try another city or date, or <Link className="home-text-link" to={postTripTo}>get started</Link>.</p>}
        <div className="home-trip-grid">{trips?.map(t => <article key={t.id} className="home-trip-card">
          <h3>{t.origin} to {t.destination}</h3>
          <p>Departs {formatDate(t.departureDate)} &middot; {t.availableCapacityKg} kg available</p>
          <p>Posted {new Date(t.createdAt).toLocaleDateString()}</p>
          <div className="home-trip-details"><span>Traveler details stay private</span><strong>USD offers</strong></div>
          <Link className="home-text-link" to={`/browse-travelers/${encodeURIComponent(t.id)}`}>View trip</Link>
          <Link className="home-button home-button-outline" data-testid="cta-send" to={user && user.role !== 'SENDER' ? '/marketplace' : `/send?travel=${encodeURIComponent(t.id)}`}>{user && user.role !== 'SENDER' ? 'Open marketplace' : 'Request space'}</Link>
        </article>)}</div>
      </>}
      <p className="home-section-note">{showingRequests ? 'DC, Maryland, and Northern Virginia share one pickup region for trip matching.' : 'Prices are agreed through agent offers; traveler request management is temporarily disabled. Browse Travelers shows advertised category prices, completed-delivery ratings, and verification without revealing identities.'}</p>
      {(!user || user.role === 'SENDER') && <Link to="/send" className="home-text-link">Post a delivery request</Link>}
    </section>

    <section className="home-how" id="how-it-works" aria-labelledby="how-title">
      <div className="home-section">
        <h2 id="how-title">How it works</h2>
        <div className="home-step-grid">{steps.map((step, index) => <article key={step.title}>
          <span className="home-step-number">{index + 1}</span>
          <h3>{step.title}</h3><p>{step.description}</p>
        </article>)}</div>
      </div>
    </section>

    <section className="home-section" id="safety" aria-labelledby="safety-title">
      <h2 id="safety-title">Built on trust</h2>
      <div className="home-trust-grid">{trust.map(item => <article className="home-trust-card" key={item.title}>
        <item.icon className="home-feature-icon" aria-hidden="true" /><h3>{item.title}</h3><p>{item.description}</p>
      </article>)}</div>
    </section>

    <section className="home-invitation">
      <h2>Going somewhere soon?</h2>
      <p>Turn spare luggage space into a connection and help someone back home.</p>
      <Link className="home-button home-button-white" to={postTripTo}>{user?.role === 'SENDER' ? 'My packages' : user && !traveler ? 'Open marketplace' : 'Post a trip'}</Link>
    </section>
    <section className="home-section home-faq" id="faq" aria-labelledby="faq-title">
      <h2 id="faq-title">Frequently asked questions</h2>
      <details><summary>How do I send a package?</summary><p>Create a sender account, add your package and receiver details, then browse trips and request a shipment. Admins manage traveler-trip requests; you approve any revised price before booking.</p></details>
      <details><summary>Can I join as an agent?</summary><p>Agent access requires administrator approval. Choose Become an Agent to view the existing access guidance; public agent signup is not available.</p></details>
      <details><summary>Are payments real?</summary><p>No. Payments use demo escrow or Stripe test mode. There are no live charges or real payouts.</p></details>
      <details><summary>When are personal details shared?</summary><p>Public traveler listings do not show names, photos, or contact details. Booking access follows the existing participant, payment, and administrator-approval rules.</p></details>
    </section>
  </div>;
}
