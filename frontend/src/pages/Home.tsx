import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PublicApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { RequestThumbnail } from '../components/RequestThumbnail';
import { filterTrips, initialTripFilters, TripListFilters } from '../components/TripListFilters';

const steps = [
  { title: 'Post or search', description: 'Senders describe the parcel. Travelers share their route and spare luggage space.' },
  { title: 'Agree on a price', description: 'Travelers send offers, and the sender picks the one that fits.' },
  { title: 'Hand over safely', description: 'Track delivery together. The sender confirms completion to release demo escrow.' },
];
const trust = [
  { title: 'Member verification', description: 'Review administrator-assigned traveler verification levels before choosing an offer.' },
  { title: 'Demo escrow', description: 'Try simulated funding and release after delivery. No real money is collected or transferred.' },
  { title: 'Clear item rules', description: 'Travelers list what they will and will not carry. Always follow airline and customs rules.' },
  { title: 'Delivery ratings', description: 'Senders rate completed deliveries, helping travelers build a real track record.' },
];
const normalize = (value: string) => value.toLowerCase().replace(/[.,]/g, '').replace(/\s+/g, ' ').trim();
const includesCity = (city: string, search: string) => normalize(city).includes(normalize(search));
const formatDate = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

export function Home() {
  const { user } = useAuth();
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
  const postTripTo = !user ? '/register?role=TRAVELER&next=%2Ftraveler'
    : traveler ? '/traveler' : user.role === 'SENDER' ? '/my-shipments' : '/marketplace';
  const trips = travel.data && filterTrips(travel.data.filter(t => includesCity(t.origin, search.from) && includesCity(t.destination, search.to)
    && (!search.date || t.departureDate === search.date)), tripFilters);
  const packages = requests.data?.filter(r => includesCity(r.pickupCity, search.from)
    && includesCity(r.destinationCity, search.to) && (!search.date || (r.availableFrom <= search.date && r.deadline >= search.date)));
  function selectMode(next: 'send' | 'travel') {
    setMode(next);
    setForm({ ...form, date: '' });
    setSearch({ ...search, date: '' });
  }

  return <div className="landing-page" id="home">
    <section className="landing-hero" aria-labelledby="hero-title">
      <div className="hero-content">
        <p className="hero-eyebrow">Peer-to-peer delivery for the Habesha community</p>
        <h1 id="hero-title">Send it with someone<br className="hidden sm:block" /> who is already going.</h1>
        <p className="hero-description">Match with travelers heading your way.<br className="sm:hidden" /> A little spare luggage space. A closer connection to home.</p>
      </div>
      <form className="landing-search" aria-label="Find a delivery connection" onSubmit={event => {
        event.preventDefault();
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
          <button className="home-button home-button-gold" type="submit">{showingRequests ? 'Find packages' : 'Find travelers'}</button>
        </div>
        <p className="search-note">{showingRequests ? 'Browse open sender requests. The departure date must fall within the package availability window. Sign in as a traveler or agent to review details and make offers.' : 'Browse upcoming traveler posts, newest first. Filter by exact departure date above or use the date range below. Confirm arrival before the delivery deadline when matching.'}</p>
      </form>
    </section>

    <section className="home-section" id="trips" ref={results} aria-labelledby="trips-title">
      <div className="home-section-heading">
        <h2 id="trips-title">{showingRequests ? 'Sender requests' : 'Traveler / agent trips'}</h2>
        <Link to={postTripTo} className="home-text-link">{user?.role === 'SENDER' ? 'My packages' : user && !traveler ? 'Open marketplace' : 'Post your trip'}</Link>
      </div>
      {showingRequests ? <>
        {requests.isPending && <p role="status">Finding sender requests...</p>}
        {requests.isError && <p role="alert">Could not load requests. <button className="home-text-link" onClick={() => requests.refetch()}>Try again</button></p>}
        {packages?.length === 0 && <p className="home-empty">No open requests match this search. Try another city or date, or <Link className="home-text-link" to="/marketplace">browse sender requests</Link>.</p>}
        <div className="home-request-list">{packages?.map(r => <article key={r.id} className="home-trip-card home-request-card">
          <RequestThumbnail id={r.id} hasPhoto={r.hasProductPhoto} publicPreview />
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
          <Link className="home-button home-button-outline" data-testid="cta-send" to={user && user.role !== 'SENDER' ? '/marketplace' : `/send?travel=${encodeURIComponent(t.id)}`}>{user && user.role !== 'SENDER' ? 'Open marketplace' : 'Request space'}</Link>
        </article>)}</div>
      </>}
      <p className="home-section-note">{showingRequests ? 'DC, Maryland, and Northern Virginia share one pickup region for trip matching.' : 'Prices are agreed through traveler offers. Public previews do not expose traveler identities or ratings.'}</p>
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
      <div className="home-trust-grid">{trust.map(item => <article className="home-trust-card" key={item.title}><h3>{item.title}</h3><p>{item.description}</p></article>)}</div>
    </section>

    <section className="home-invitation">
      <h2>Going somewhere soon?</h2>
      <p>Turn spare luggage space into a connection and help someone back home.</p>
      <Link className="home-button home-button-white" to={postTripTo}>{user?.role === 'SENDER' ? 'My packages' : user && !traveler ? 'Open marketplace' : 'Post a trip'}</Link>
    </section>
  </div>;
}
