import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthContext';
import { MarketplaceApi, deliveryStatuses, itemTypes } from '../api/marketplace';
import type { ItemType, MarketRequest, MarketTraveler, MarketTrip, VerificationLevel } from '../api/marketplace';

export const display = (value: string) => value.replaceAll('_', ' ');
export const money = (value: number) => new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(value);
export function marketError(error: unknown) {
  toast.error(isAxiosError<{ message?: string }>(error) ? error.response?.data?.message || 'Marketplace action failed. Please retry.' : 'Marketplace action failed. Please retry.');
}
export function DemoNotice() {
  return <p role="note" className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950">
    <strong>Demo escrow only.</strong> Funding, release and refunds are simulated. No real money is collected, held or transferred. All prices are USD.
  </p>;
}
export function TravelerReputation({ traveler }: { traveler: MarketTraveler }) {
  return <p className="text-sm text-ink-600">{traveler.name} &middot; {traveler.role === 'AGENT' ? 'Agent' : 'Traveler'} &middot; {traveler.rating == null ? 'Not yet rated' : `${traveler.rating.toFixed(1)}/5 (${traveler.ratingCount} ${traveler.ratingCount === 1 ? 'rating' : 'ratings'})`} &middot; {display(traveler.verification)}</p>;
}
export function RequestCard({ request, score }: { request: MarketRequest; score?: number }) {
  return <article className="card space-y-3">
    <div className="flex flex-wrap justify-between gap-2"><h2 className="text-lg font-bold">{request.pickupCity} &rarr; {request.destinationCity}</h2>
      <span className="text-xs font-bold rounded-full bg-blue-100 text-blue-900 px-3 py-1">{display(request.status)}</span></div>
    <p>{display(request.itemType)} &middot; {request.weightKg} kg &middot; Offered {money(request.offeredPrice)}</p>
    <p className="text-sm">Available {request.availableFrom} &middot; Deadline {request.deadline}</p>
    <p className="text-sm break-words">{request.description}</p>
    {score != null && <p className="text-sm font-semibold">Match score: {score.toFixed(2)}</p>}
    <Link to={`/marketplace/requests/${request.id}`} className="btn btn-primary">View request and offers</Link>
  </article>;
}
export function TripCard({ trip }: { trip: MarketTrip }) {
  return <article className="card space-y-2">
    <h2 className="text-lg font-bold">{trip.origin} &rarr; {trip.destination}</h2>
    <TravelerReputation traveler={trip.traveler} />
    <p>Departure {trip.departureDate}{trip.expectedArrivalDate && <> &middot; Arrival {trip.expectedArrivalDate}</>}</p>
    <p>{trip.availableKg} of {trip.capacityKg} kg available</p>
    <p className="text-sm">Will carry: {trip.allowedItems.map(display).join(', ')}</p>
    <p className="text-sm">Will not carry: {trip.blockedItems.map(display).join(', ') || 'None specified'}</p>
  </article>;
}
function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function MarketRequestForm() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const preferredTrip = params.get('travel');
  const trips = useQuery({ queryKey: ['marketplace', 'trips'], queryFn: MarketplaceApi.trips, enabled: !!preferredTrip });
  const [form, setForm] = useState({ pickupCity: '', destinationCity: '', availableFrom: today(), deadline: '', itemType: 'OTHER' as ItemType,
    description: '', weightKg: '', offeredPrice: '' });
  const selectedTrip = trips.data?.find(t => t.id === preferredTrip);
  const appliedTrip = useRef('');
  useEffect(() => {
    if (selectedTrip && appliedTrip.current !== selectedTrip.id) {
      appliedTrip.current = selectedTrip.id;
      setForm(current => ({ ...current, pickupCity: selectedTrip.origin,
        destinationCity: selectedTrip.destination, deadline: selectedTrip.expectedArrivalDate || selectedTrip.departureDate }));
    }
  }, [selectedTrip]);
  const mutation = useMutation({
    mutationFn: () => MarketplaceApi.createRequest({ ...form, weightKg: Number(form.weightKg), offeredPrice: Number(form.offeredPrice) }),
    onSuccess: request => { qc.invalidateQueries({ queryKey: ['marketplace'] }); toast.success('Request posted. Review suggested matches and traveler offers.'); navigate(`/marketplace/requests/${request.id}`); },
    onError: marketError,
  });
  return <div className="max-w-2xl mx-auto space-y-5">
    <h1 className="text-3xl font-bold">Post a delivery request</h1><DemoNotice />
    {selectedTrip && <TripCard trip={selectedTrip} />}
    {trips.isError && <p role="alert">Could not load the selected trip. <button className="underline" onClick={() => trips.refetch()}>Retry</button></p>}
    <p className="text-sm">Travelers and agents send offers, then you choose one. Posting does not reserve a trip. Do not include contact details in the public description.</p>
    <form className="card grid sm:grid-cols-2 gap-4" onSubmit={e => { e.preventDefault(); mutation.mutate(); }}>
      <label className="label">Pickup city<input className="input" required maxLength={100} value={form.pickupCity} onChange={e => setForm({ ...form, pickupCity: e.target.value })} /></label>
      <label className="label">Destination city<input className="input" required maxLength={100} value={form.destinationCity} onChange={e => setForm({ ...form, destinationCity: e.target.value })} /></label>
      <label className="label">Available from<input className="input" type="date" required min={today()} value={form.availableFrom} onChange={e => setForm({ ...form, availableFrom: e.target.value })} /></label>
      <label className="label">Delivery deadline<input className="input" type="date" required min={form.availableFrom} value={form.deadline} onChange={e => setForm({ ...form, deadline: e.target.value })} /></label>
      <label className="label">Item type<select className="input" value={form.itemType} onChange={e => {
        const itemType = itemTypes.find(type => type === e.target.value);
        if (itemType) setForm({ ...form, itemType });
      }}>{itemTypes.map(type => <option key={type} value={type}>{display(type)}</option>)}</select></label>
      <label className="label">Weight (kg)<input className="input" type="number" min="0.1" max="99999999.99" step="0.01" required value={form.weightKg} onChange={e => setForm({ ...form, weightKg: e.target.value })} /></label>
      <label className="label">Offered price (USD)<input className="input" type="number" min="0.01" max="99999999.99" step="0.01" required value={form.offeredPrice} onChange={e => setForm({ ...form, offeredPrice: e.target.value })} /></label>
      <label className="label sm:col-span-2">Item description<textarea className="input" required maxLength={1000} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
      <button className="btn btn-brand sm:col-span-2" disabled={mutation.isPending}>{mutation.isPending ? 'Posting...' : 'Post request'}</button>
    </form>
  </div>;
}

function TripForm() {
  const qc = useQueryClient();
  const [form, setForm] = useState({ origin: '', destination: '', departureDate: '', expectedArrivalDate: '', capacityKg: '' });
  const [allowed, setAllowed] = useState<ItemType[]>([]);
  const [blocked, setBlocked] = useState<ItemType[]>([]);
  const mutation = useMutation({
    mutationFn: () => MarketplaceApi.createTrip({ ...form, expectedArrivalDate: form.expectedArrivalDate || undefined,
      capacityKg: Number(form.capacityKg), allowedItems: allowed, blockedItems: blocked }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketplace'] }); qc.invalidateQueries({ queryKey: ['public-travel'] }); toast.success('Trip posted'); setForm({ origin: '', destination: '', departureDate: '', expectedArrivalDate: '', capacityKg: '' }); },
    onError: marketError,
  });
  function rule(type: ItemType, value: string) {
    setAllowed(current => [...current.filter(t => t !== type), ...(value === 'allow' ? [type] : [])]);
    setBlocked(current => [...current.filter(t => t !== type), ...(value === 'block' ? [type] : [])]);
  }
  return <form className="card grid sm:grid-cols-2 gap-4" onSubmit={e => { e.preventDefault(); mutation.mutate(); }}>
    <h2 className="text-xl font-bold sm:col-span-2">Post a traveler trip</h2>
    <label className="label">Origin city<input className="input" required maxLength={100} value={form.origin} onChange={e => setForm({ ...form, origin: e.target.value })} /></label>
    <label className="label">Destination city<input className="input" required maxLength={100} value={form.destination} onChange={e => setForm({ ...form, destination: e.target.value })} /></label>
    <label className="label">Departure date<input className="input" type="date" required min={today()} value={form.departureDate} onChange={e => setForm({ ...form, departureDate: e.target.value })} /></label>
    <label className="label">Expected arrival (optional)<input className="input" type="date" min={form.departureDate || today()} value={form.expectedArrivalDate} onChange={e => setForm({ ...form, expectedArrivalDate: e.target.value })} /></label>
    <label className="label">Available weight (kg)<input className="input" type="number" min="0.1" max="99999999.99" step="0.01" required value={form.capacityKg} onChange={e => setForm({ ...form, capacityKg: e.target.value })} /></label>
    <fieldset className="sm:col-span-2 grid sm:grid-cols-2 gap-3"><legend className="font-semibold mb-2">Item rules</legend>
      <p className="sm:col-span-2 text-sm">Only explicitly allowed categories can match. Select at least one.</p>
      {itemTypes.map(type => <label key={type} className="label">{display(type)}<select className="input"
        value={allowed.includes(type) ? 'allow' : blocked.includes(type) ? 'block' : ''} onChange={e => rule(type, e.target.value)}>
        <option value="">Not accepted</option><option value="allow">Will carry</option><option value="block">Will not carry</option>
      </select></label>)}
    </fieldset>
    <button className="btn btn-brand sm:col-span-2" disabled={mutation.isPending || allowed.length === 0}>{mutation.isPending ? 'Posting...' : 'Publish trip'}</button>
  </form>;
}

export function Marketplace({ tripsOnly = false, myPackagesOnly = false }: { tripsOnly?: boolean; myPackagesOnly?: boolean }) {
  const { user } = useAuth();
  const canOffer = user?.role === 'TRAVELER' || user?.role === 'AGENT';
  const qc = useQueryClient();
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const [tripId, setTripId] = useState('');
  const showRequests = myPackagesOnly || (!tripsOnly && user?.role !== 'SENDER');
  const requests = useQuery({ queryKey: ['marketplace', 'requests', user?.id], queryFn: MarketplaceApi.requests, enabled: showRequests, refetchInterval: 15_000 });
  const trips = useQuery({ queryKey: ['marketplace', 'trips', user?.id], queryFn: MarketplaceApi.trips, enabled: !myPackagesOnly, refetchInterval: 15_000 });
  const matches = useQuery({ queryKey: ['marketplace', 'matches', tripId, user?.id], queryFn: () => MarketplaceApi.tripMatches(tripId), enabled: !!tripId, refetchInterval: 15_000 });
  const verify = useMutation({ mutationFn: ({ id, level }: { id: string; level: VerificationLevel }) => MarketplaceApi.verify(id, level),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketplace'] }); toast.success('Verification updated'); }, onError: marketError });
  const owned = trips.data?.filter(t => t.traveler.id === user?.id) ?? [];
  const filtered = requests.data?.filter(r => (!myPackagesOnly || r.senderId === user?.id)
    && (!status || r.status === status) && `${r.pickupCity} ${r.destinationCity} ${r.description}`.toLowerCase().includes(search.toLowerCase()));
  return <div className="space-y-6">
    <div className="flex flex-wrap justify-between items-center gap-3"><h1 className="text-3xl font-bold">{myPackagesOnly ? 'My packages' : user?.role === 'SENDER' ? 'Traveler / agent trips' : tripsOnly ? 'My delivery trips' : canOffer ? 'Sender requests' : 'Delivery marketplace'}</h1>
      {user?.role === 'SENDER' && <Link to="/send" className="btn btn-brand">Post a request</Link>}
      {!myPackagesOnly && (user?.role === 'SENDER'
        ? <Link className="btn btn-ghost" to="/my-shipments">My packages</Link>
        : <Link className="btn btn-ghost" to={tripsOnly ? '/marketplace' : '/traveler'}>{tripsOnly ? 'Browse requests' : canOffer ? 'Post a trip' : 'Delivery trips'}</Link>)}
    </div>
    {myPackagesOnly ? <p className="text-sm text-ink-500">Only packages you created are listed here. Open a package to review offers and delivery progress.</p> : <DemoNotice />}
    {tripsOnly && canOffer && <TripForm />}
    {canOffer && <section className="card space-y-3">
      <h2 className="text-xl font-semibold">Suggested requests for your trip</h2>
      <label className="label">Your trip<select className="input" value={tripId} onChange={e => setTripId(e.target.value)}>
        <option value="">Choose a trip</option>{owned.map(t => <option key={t.id} value={t.id}>{t.origin} to {t.destination} - {t.departureDate}</option>)}
      </select></label>
      {owned.length === 0 && <p>Post a trip to receive matching suggestions.</p>}
      {matches.isFetching && <p role="status">Loading suggestions...</p>}
      {matches.isError && <p role="alert">Could not load suggestions. <button className="underline" onClick={() => matches.refetch()}>Retry</button></p>}
      {matches.data?.length === 0 && <p>No compatible requests. Matching compares cities, date window, remaining weight and item rules.</p>}
      {matches.data?.map(m => <div key={m.request.id}><RequestCard request={m.request} score={m.score} /><Link className="underline" to={`/marketplace/requests/${m.request.id}?trip=${tripId}`}>Make an offer using this trip</Link></div>)}
    </section>}
    {showRequests && <>
      <div className="flex flex-wrap gap-3">
        <label className="label">Status<select className="input" value={status} onChange={e => setStatus(e.target.value)}><option value="">All statuses</option>{deliveryStatuses.map(s => <option key={s}>{s}</option>)}</select></label>
        <label className="label flex-1">Search requests<input className="input" value={search} onChange={e => setSearch(e.target.value)} /></label>
      </div>
      {requests.isPending && <p role="status">Loading delivery requests...</p>}
      {requests.isError && <p role="alert">Could not load requests. <button className="underline" onClick={() => requests.refetch()}>Retry</button></p>}
      {filtered?.length === 0 && <p>No delivery requests found.</p>}
      <div className="grid md:grid-cols-2 gap-4">{filtered?.map(r => <RequestCard key={r.id} request={r} />)}</div>
    </>}
    {!myPackagesOnly && <><section className="space-y-4"><h2 className="text-2xl font-bold">{canOffer ? 'Your posted trips' : 'Available traveler / agent trips'}</h2>
      {trips.isPending && <p role="status">Loading trips...</p>}
      {trips.isError && <p role="alert">Could not load trips. <button className="underline" onClick={() => trips.refetch()}>Retry</button></p>}
      <div className="grid md:grid-cols-2 gap-4">{(canOffer ? owned : trips.data)?.map(t => <div key={t.id} className="space-y-2"><TripCard trip={t} />
        {user?.role === 'SENDER' && <Link className="btn btn-primary" to={`/send?travel=${encodeURIComponent(t.id)}`}>Post a request for this trip</Link>}
        {user?.role === 'ADMIN' && <div className="flex flex-wrap gap-2">{(['UNVERIFIED', 'ID_VERIFIED', 'FULLY_VERIFIED'] as const).map(level =>
          <button key={level} className="btn btn-ghost text-xs" disabled={verify.isPending || level === t.traveler.verification}
            onClick={() => verify.mutate({ id: t.traveler.id, level })}>Set {display(level)}</button>)}</div>}
      </div>)}</div>
    </section>
    <p className="text-sm text-ink-500">DC, Maryland, and Northern Virginia share one pickup region. Other pickup cities and all destinations must match by name (case and extra spaces ignored). Departure must be on or after the sender's drop-off (available-from) date and arrival on or before the deadline, including those dates. Without an arrival date, suggestions use departure date only. New travelers are unrated, not given a fictional rating.</p>
    <div className="flex flex-wrap gap-4"><Link to="/legacy-shipments" className="underline text-sm">View legacy operational shipments</Link>
      {(user?.role === 'TRAVELER' || user?.role === 'ADMIN') && <Link to="/legacy-trips" className="underline text-sm">Legacy operational trips</Link>}</div></>}
  </div>;
}
