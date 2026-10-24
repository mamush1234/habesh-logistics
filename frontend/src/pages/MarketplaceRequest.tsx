import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthContext';
import { MarketplaceApi } from '../api/marketplace';
import type { DeliveryStatus } from '../api/marketplace';
import { DemoNotice, display, marketError, money, TravelerReputation, TripCard } from './Marketplace';
import { PackageChat } from '../components/PackageChat';
import { GuestShipmentConversion } from '../components/GuestShipmentConversion';

export function MarketplaceRequest() {
  const { id = '' } = useParams();
  const [params] = useSearchParams();
  const { user } = useAuth();
  const qc = useQueryClient();
  const [tripId, setTripId] = useState(params.get('trip') || '');
  const [price, setPrice] = useState('');
  const [message, setMessage] = useState('');
  const [reason, setReason] = useState('');
  const [rating, setRating] = useState('5');
  const query = useQuery({ queryKey: ['marketplace', 'request', id, user?.id], queryFn: () => MarketplaceApi.request(id), refetchInterval: 10_000 });
  const r = query.data;
  const sender = r?.senderId === user?.id;
  const admin = user?.role === 'ADMIN';
  const readOnlyTraveler = user?.role === 'TRAVELER';
  const managesSender = !readOnlyTraveler && ((sender && !r?.guestRequest) || (admin && r?.guestRequest));
  const canOffer = user?.role === 'AGENT';
  const accepted = r?.offers.find(o => o.id === r.acceptedOfferId);
  const carrier = accepted?.traveler.id === user?.id;
  const matches = useQuery({ queryKey: ['marketplace', 'request-matches', id, user?.id], queryFn: () => MarketplaceApi.requestMatches(id),
    enabled: !!r && ((sender && !r.guestRequest) || admin || canOffer) && r.status === 'REQUESTED', refetchInterval: 15_000 });
  const photo = useQuery({ queryKey: ['marketplace', 'product-photo', id, user?.id], queryFn: () => MarketplaceApi.productPhoto(id),
    enabled: !!r?.hasProductPhoto, staleTime: 5 * 60_000 });
  const eligibleTrips = matches.data?.map(m => m.trip) ?? [];
  const selectedTrip = eligibleTrips.find(t => t.id === tripId);
  const mutation = useMutation({ mutationFn: (action: () => Promise<unknown>) => action(),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketplace'] }); qc.invalidateQueries({ queryKey: ['public-travel'] }); qc.invalidateQueries({ queryKey: ['public-requests'] }); toast.success('Delivery updated'); },
    onError: error => { qc.invalidateQueries({ queryKey: ['marketplace'] }); marketError(error); } });
  const act = (action: () => Promise<unknown>) => mutation.mutate(action);
  const transition = (status: DeliveryStatus) => act(() => MarketplaceApi.transition(id, status, reason || undefined));
  if (query.isPending) return <p role="status">Loading delivery request...</p>;
  if (query.isError || !r) return <p role="alert">Could not load this request. It may no longer be available to your account. <button className="underline" onClick={() => query.refetch()}>Retry</button></p>;
  const pending = r.offers.find(o => o.traveler.id === user?.id && o.status === 'PENDING');
  return <div className="space-y-5 max-w-5xl mx-auto">
    <Link to="/marketplace" className="underline">Back to marketplace</Link>
    <h1 className="text-3xl font-bold">{r.pickupCity} &rarr; {r.destinationCity}</h1><DemoNotice />
    <section className="card space-y-3">
      <h2 className="text-xl font-bold">Delivery request &middot; {r.convertedShipmentId ? 'CONVERTED TO SHIPMENT' : display(r.status)}</h2>
      <p className="font-semibold">{r.adminApproved ? 'Admin approved' : 'Awaiting admin approval'} &middot; Traveler access is read-only.</p>
      {admin && <button className="btn btn-brand" disabled={mutation.isPending}
        onClick={() => act(() => MarketplaceApi.setApproval(id, !r.adminApproved))}>
        {r.adminApproved ? 'Remove traveler approval' : 'Approve for traveler viewing'}
      </button>}
      {readOnlyTraveler && <p role="note">You can view admin-approved requests only. Offers, request management and sending chat messages are temporarily disabled for travelers. Contact an administrator to coordinate.</p>}
      <p>{display(r.itemType)} &middot; {r.weightKg} kg &middot; Offered {money(r.offeredPrice)}</p>
      <p>Available {r.availableFrom} &middot; Deadline {r.deadline}</p>
      <p className="break-words">{r.description}</p>
      {r.guestRequest && <p className="rounded-lg bg-blue-50 p-3 text-sm">Admin-managed sender request. An administrator handles offers and contacts the sender directly. Newly created senders can view their own request but cannot change offers or processing. Package chat is not available for these requests.</p>}
      {r.hasProductPhoto && <div>
        <h3 className="font-semibold">Product photo</h3>
        {photo.isPending && <p role="status">Loading product photo...</p>}
        {photo.isError && <p role="alert">Could not load product photo. <button className="underline" onClick={() => photo.refetch()}>Retry</button></p>}
        {photo.data && <img src={photo.data} alt="Product for this delivery request" className="max-h-80 max-w-full rounded-lg object-contain" />}
      </div>}
      <p>Demo escrow: {display(r.escrowStatus)}{r.escrowAmount != null && <> &middot; {money(r.escrowAmount)}</>}</p>
      {r.disputeReason && <p className="text-red-700 break-words">Dispute: {r.disputeReason}</p>}
      {r.senderContact && <p className="text-sm break-words">Sender: {r.senderContact.name} &middot; {r.senderContact.email} {r.senderContact.phone}</p>}
      {!r.senderContact && r.senderName && <p className="text-sm break-words">Sender: {r.senderName}</p>}
      {r.travelerContact && <p className="text-sm break-words">Traveler / agent: {r.travelerContact.name} &middot; {r.travelerContact.email} {r.travelerContact.phone}</p>}
      {!r.guestRequest && !r.travelerContact && <p className="text-sm text-ink-500">Participant names unlock when an offer is accepted. Email and phone details unlock for the selected participants after demo escrow funding.</p>}
    </section>
    {admin && r.guestRequest && <GuestShipmentConversion key={r.id} request={r} />}
    {!r.guestRequest && r.acceptedOfferId && (sender || carrier || admin) && <PackageChat key={`${id}:${user?.id}`} requestId={id} closed={r.status === 'CANCELLED' || r.status === 'COMPLETED'} />}
    {r.status === 'REQUESTED' && canOffer && !pending && <form className="card space-y-4" onSubmit={e => { e.preventDefault(); if (selectedTrip) act(() => MarketplaceApi.offer(id, selectedTrip.id, Number(price), message)); }}>
      <h2 className="text-xl font-bold">Send an offer</h2>
      {matches.isPending && <p role="status">Checking your trips for a match...</p>}
      {matches.isError && <p role="alert">Could not check your trips. <button type="button" className="underline" onClick={() => matches.refetch()}>Retry</button></p>}
      {matches.isSuccess && eligibleTrips.length === 0 && <p role="status">None of your trips match this request. Pickup locations must match by name or share the DC/Maryland/Northern Virginia region. Destination names must match (ignoring case and extra spaces), departure must be on or after {r.availableFrom}, arrival by {r.deadline}, and the trip must allow {display(r.itemType)} with at least {r.weightKg} kg available. <Link className="underline" to="/traveler">Post a matching trip</Link>.</p>}
      <label className="label">Your compatible trip<select className="input" required value={selectedTrip?.id ?? ''} onChange={e => setTripId(e.target.value)}><option value="">Select a compatible trip</option>
        {eligibleTrips.map(t => <option key={t.id} value={t.id}>{t.origin} to {t.destination} - departs {t.departureDate} - arrives {t.expectedArrivalDate ?? t.departureDate} - {t.availableKg} kg</option>)}</select></label>
      <label className="label">Your price (USD)<input type="number" className="input" required min="0.01" max="99999999.99" step="0.01" value={price} onChange={e => setPrice(e.target.value)} /></label>
      <label className="label">Message (no contact details)<textarea className="input" maxLength={500} value={message} onChange={e => setMessage(e.target.value)} /></label>
      <div className="flex flex-wrap gap-2"><button className="btn btn-primary" disabled={!selectedTrip || matches.isError || mutation.isPending}>Send offer</button>
        <button type="button" className="btn btn-brand" disabled={!selectedTrip || matches.isError || mutation.isPending} onClick={() => selectedTrip && act(() => MarketplaceApi.offer(id, selectedTrip.id, r.offeredPrice, message))}>Accept asking price {money(r.offeredPrice)}</button></div>
      <p className="text-sm">Accepting the asking price sends an offer. {r.guestRequest ? 'An admin must select you for the guest sender.' : 'The sender must still select you.'} Weight is reserved only after offer acceptance.</p>
    </form>}
    {!(sender && r.guestRequest) && (!readOnlyTraveler || r.offers.length > 0) && <section className="card space-y-4">
      <h2 className="text-xl font-bold">{readOnlyTraveler ? 'Previous offers (read-only)' : sender || admin ? 'Traveler / agent offers (ranked)' : 'Your offers'}</h2>
      {r.offers.length === 0 && <p>No offers yet.</p>}
      {r.offers.map(o => <article key={o.id} className="border-t py-3 space-y-2">
        <TravelerReputation traveler={o.traveler} /><p className="font-semibold">{money(o.price)} &middot; {display(o.status)} &middot; Score {o.score.toFixed(2)}</p>
        {o.message && <p className="text-sm break-words">{o.message}</p>}
        <div className="flex flex-wrap gap-2">
          {managesSender && r.status === 'REQUESTED' && o.status === 'PENDING' && <>
            {o.traveler.role !== 'TRAVELER' ? <button className="btn btn-brand" disabled={mutation.isPending} onClick={() => act(() => MarketplaceApi.decideOffer(id, o.id, 'accept'))}>Accept this offer</button>
              : <p>Traveler offers are temporarily disabled; this previous offer cannot be accepted.</p>}
            <button className="btn btn-ghost" disabled={mutation.isPending} onClick={() => act(() => MarketplaceApi.decideOffer(id, o.id, 'decline'))}>Decline</button></>}
          {!readOnlyTraveler && o.traveler.id === user?.id && o.status === 'PENDING' && <button className="btn btn-ghost" disabled={mutation.isPending} onClick={() => act(() => MarketplaceApi.decideOffer(id, o.id, 'withdraw'))}>Withdraw offer</button>}
        </div>
      </article>)}
      <p className="text-xs text-ink-500">Ranking: price 50%, completed-delivery ratings 30%, admin-assigned verification 20%. Lower prices favor senders; higher offered prices favor travelers. No rating history contributes zero rating points.</p>
    </section>}
    {!readOnlyTraveler && ((sender && !r.guestRequest) || carrier || admin) && !r.convertedShipmentId && <section className="card space-y-4">
      <h2 className="text-xl font-bold">Delivery actions</h2>
      {managesSender && r.status === 'MATCHED' && <button className="btn btn-brand" disabled={mutation.isPending} onClick={() => act(() => MarketplaceApi.fund(id))}>Simulate escrow funding {money(accepted?.price ?? r.offeredPrice)} - no real payment</button>}
      <div className="flex flex-wrap gap-2">
        {(carrier || admin) && r.status === 'PAID_ESCROW' && <button className="btn btn-primary" disabled={mutation.isPending} onClick={() => transition('PICKED_UP')}>Mark picked up</button>}
        {(carrier || admin) && r.status === 'PICKED_UP' && <button className="btn btn-primary" disabled={mutation.isPending} onClick={() => transition('IN_TRANSIT')}>Mark in transit</button>}
        {(carrier || admin) && r.status === 'IN_TRANSIT' && <button className="btn btn-primary" disabled={mutation.isPending} onClick={() => transition('DELIVERED')}>Mark delivered</button>}
        {(sender || admin) && r.status === 'DELIVERED' && <button className="btn btn-brand" disabled={mutation.isPending} onClick={() => transition('COMPLETED')}>Confirm receipt and release demo escrow</button>}
        {['REQUESTED', 'MATCHED', 'PAID_ESCROW'].includes(r.status) && <button className="btn btn-ghost" disabled={mutation.isPending} onClick={() => transition('CANCELLED')}>Cancel delivery{r.status === 'PAID_ESCROW' ? ' and simulate refund' : ''}</button>}
      </div>
      {(['PAID_ESCROW', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED'].includes(r.status) || (admin && r.status === 'DISPUTED')) && <>
        <label className="label">{r.status === 'DISPUTED' ? 'Resolution reason' : 'Dispute reason'}<textarea className="input" maxLength={1000} value={reason} onChange={e => setReason(e.target.value)} /></label>
        {r.status !== 'DISPUTED' ? <button className="btn btn-ghost text-red-700" disabled={!reason.trim() || mutation.isPending} onClick={() => transition('DISPUTED')}>Open dispute and freeze demo escrow</button>
          : <div className="flex flex-wrap gap-2"><button className="btn btn-brand" disabled={!reason.trim() || mutation.isPending} onClick={() => transition('COMPLETED')}>Resolve: release demo escrow</button>
            <button className="btn btn-ghost" disabled={!reason.trim() || mutation.isPending} onClick={() => transition('CANCELLED')}>Resolve: refund demo escrow</button></div>}
      </>}
      {r.status === 'DISPUTED' && !admin && <p>Demo escrow is frozen until an administrator resolves the dispute.</p>}
      {sender && r.status === 'COMPLETED' && r.rating == null && <div className="flex flex-wrap gap-2 items-end">
        <label className="label">Rate your traveler<select className="input" value={rating} onChange={e => setRating(e.target.value)}>{[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n} / 5</option>)}</select></label>
        <button className="btn btn-brand" disabled={mutation.isPending} onClick={() => act(() => MarketplaceApi.rate(id, Number(rating)))}>Submit rating</button>
      </div>}
      {r.rating != null && <p>Traveler rating: {r.rating}/5</p>}
    </section>}
    {((sender && !r.guestRequest) || admin) && r.status === 'REQUESTED' && <section className="space-y-4">
      <h2 className="text-xl font-bold">Suggested traveler matches</h2>
      {matches.isPending && <p role="status">Finding compatible trips...</p>}
      {matches.isError && <p role="alert">Could not load matches. <button className="underline" onClick={() => matches.refetch()}>Retry</button></p>}
      {matches.data?.length === 0 && <p>No matching trips yet. New trips are checked as they are posted.</p>}
      {matches.data?.map(m => <div key={m.trip.id} className="space-y-1"><TripCard trip={m.trip} /><p className="text-sm">Match score {m.score.toFixed(2)} &middot; {money(m.price)} {m.request.offers.some(o => o.tripId === m.trip.id && o.status === 'PENDING') ? 'offered' : 'request price (no offer yet)'}</p></div>)}
    </section>}
    {r.timeline.length > 0 && <section className="card space-y-3"><h2 className="text-xl font-bold">Delivery timeline</h2>
      <ol className="space-y-3">{r.timeline.map((event, index) => <li key={index} className="text-sm break-words"><strong>{display(event.status)}</strong> &middot; {new Date(event.createdAt).toLocaleString()}<p>{event.description}</p></li>)}</ol>
    </section>}
  </div>;
}
