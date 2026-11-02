import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { ShipmentRequestsApi } from '../api/shipmentRequests';
import type { ShipmentDecision, ShipmentQuote, ShipmentRequest } from '../api/shipmentRequests';
import { money, display } from '../pages/Marketplace';
import { useAuth } from '../auth/AuthContext';

export function ShipmentPriceSummary({ quote, payment = false }: { quote: ShipmentQuote; payment?: boolean }) {
  return <div className="space-y-3">
    <h3 className="font-bold">Price summary (USD)</h3>
    {quote.items.length === 0 && <p className="text-sm">Custom package price proposal replaces the advertised item subtotal.</p>}
    <ul className="space-y-2">{quote.items.map((line, index) => <li key={index} className="flex flex-wrap justify-between gap-2 text-sm">
      <span>{display(line.category)}: {line.unit === 'PER_KG' ? `${line.weightKg} kg x ${money(line.rate)} per kg` : `1 listed item x ${money(line.rate)} per item`}</span>
      <strong>{money(line.amount)}</strong>
    </li>)}</ul>
    <dl className="space-y-2 border-t pt-3">
      <div className="flex justify-between gap-3"><dt>Package amount</dt><dd>{money(quote.packageAmount)}</dd></div>
      <div className="flex justify-between gap-3"><dt>Platform fee ({quote.platformFeePercent}%)</dt><dd>{money(quote.platformFee)}</dd></div>
      <div className="flex justify-between gap-3"><dt>Fee payer</dt><dd>{display(quote.feePayer)}</dd></div>
      <div className="flex justify-between gap-3"><dt>Sender fee share</dt><dd>{money(quote.senderFee)}</dd></div>
      <div className="flex justify-between gap-3 font-bold text-emerald-800"><dt>Total payable after acceptance</dt><dd>{money(quote.total)}</dd></div>
    </dl>
    <p className="text-sm text-ink-500">Carrier fee share: {money(quote.travelerFee)}. Carrier payout before any agent commission: {money(quote.travelerPayout)}.
      Prices are rounded to cents per listed item. {payment ? 'Stripe test mode only; no real funds are collected.' : 'No payment is collected now.'}</p>
  </div>;
}

export function ShipmentRequestSummary({ request }: { request: ShipmentRequest }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const seconds = Math.max(0, Math.ceil((Date.parse(request.expiresAt) - now) / 1000));
  const timedOut = request.status === 'PENDING' && seconds === 0;
  return <article className="rounded-xl border border-ink-300 p-4 space-y-3 min-w-0">
    <div className="flex flex-wrap justify-between gap-3">
      <h3 className="font-bold break-words">{request.quote.origin} &rarr; {request.quote.destination}</h3>
      <span className={`rounded-full px-3 py-1 text-xs font-bold ${request.status === 'ACCEPTED' ? 'bg-emerald-100 text-emerald-900'
        : request.status === 'PENDING' && !timedOut ? 'bg-amber-100 text-amber-900' : 'bg-red-50 text-red-800'}`}>
        {timedOut ? 'Expired - refreshing status' : request.status}
      </span>
    </div>
    <p className="text-sm">Sent {new Date(request.createdAt).toLocaleString()} &middot; Expires {new Date(request.expiresAt).toLocaleString()}</p>
    {request.status === 'PENDING' && !timedOut && <p className="font-semibold text-sm">Review time remaining: {Math.floor(seconds / 60)}m {seconds % 60}s</p>}
    <p className="text-sm">{request.adminManaged ? 'Admin-managed review. Traveler request management remains disabled.' : 'Review handled by the trip-owning agent or an administrator.'}
      {' '}{request.status === 'ACCEPTED' ? 'Trip capacity reserved. The sender can open Stripe test payment from the package details. No real payments or payouts.'
        : 'No capacity is reserved and payment is not available.'}</p>
    {request.message && <p className="text-sm whitespace-pre-wrap break-words">Message: {request.message}</p>}
    <ShipmentPriceSummary quote={request.quote} />
    {request.proposedQuote && <section className="rounded-xl bg-amber-50 p-4 space-y-3">
      <h3 className="font-bold">{request.status === 'ACCEPTED' ? 'Agreed revised price' : 'Revised price proposal'}</h3>
      <ShipmentPriceSummary quote={request.proposedQuote} />
      {request.status === 'PENDING' && <p className="text-sm">Awaiting sender approval. The original expiry time still applies.</p>}
    </section>}
    {request.decidedAt && <p className="text-sm">Decision recorded {new Date(request.decidedAt).toLocaleString()}</p>}
    {request.status === 'PENDING' && !timedOut && <ShipmentRequestActions request={request} />}
    <Link className="btn btn-ghost" to={`/marketplace/requests/${request.packageId}`}>View package details</Link>
    <Link className="underline" to={`/browse-travelers/${request.tripId}`}>View selected trip</Link>
  </article>;
}

function ShipmentRequestActions({ request }: { request: ShipmentRequest }) {
  const [price, setPrice] = useState('');
  const proposal = request.proposedQuote;
  const qc = useQueryClient();
  const mutation = useMutation({
    mutationFn: (input: ShipmentDecision) => ShipmentRequestsApi.decide(request.id, input),
    onSuccess: updated => {
      qc.invalidateQueries({ queryKey: ['marketplace'] });
      qc.invalidateQueries({ queryKey: ['public-travel'] });
      qc.invalidateQueries({ queryKey: ['public-requests'] });
      qc.invalidateQueries({ queryKey: ['admin-console'] });
      qc.invalidateQueries({ queryKey: ['agent'] });
      setPrice('');
      toast.success(updated.status === 'EXPIRED' ? 'Request expired; no action was taken'
        : updated.status === 'PENDING' ? 'Revised price sent for sender approval' : `Request ${updated.status.toLowerCase()}`);
    },
    onError: () => qc.invalidateQueries({ queryKey: ['marketplace'] }),
  });
  const error = isAxiosError<{ message?: string }>(mutation.error)
    ? mutation.error.response?.data?.message || 'Could not update this request. Please retry.'
    : 'Could not update this request. Please retry.';
  const decide = (action: ShipmentDecision['action'], quoteKey: string) => mutation.mutate({ action, quoteKey });
  return <div className="space-y-3 border-t pt-3">
    {mutation.isError && <p role="alert">{error}</p>}
    {request.canReview && <>
      <div className="flex flex-wrap gap-3">
        {!request.proposedQuote && <button className="btn btn-primary" disabled={mutation.isPending}
          onClick={() => decide('ACCEPT', request.quote.quoteKey)}>Accept request and reserve capacity</button>}
        <button className="btn btn-ghost text-red-700" disabled={mutation.isPending}
          onClick={() => decide('DECLINE', request.quote.quoteKey)}>Decline request</button>
      </div>
      {request.proposedQuote && <p className="text-sm">The sender must approve the revised total before acceptance.</p>}
      <form className="flex flex-wrap items-end gap-3" onSubmit={e => {
        e.preventDefault();
        mutation.mutate({ action: 'PROPOSE_PRICE', proposedPrice: Number(price), quoteKey: request.quote.quoteKey });
      }}>
        <label className="label">Proposed package price (USD, before fees)
          <input className="input" type="number" min="0.01" max="99999999.99" step="0.01" required
            disabled={mutation.isPending} value={price} onChange={e => setPrice(e.target.value)} />
        </label>
        <button className="btn btn-ghost" disabled={mutation.isPending || !price}>Propose price</button>
      </form>
    </>}
    {request.canRespond && proposal && <div className="flex flex-wrap gap-3">
      <button className="btn btn-primary" disabled={mutation.isPending}
        onClick={() => decide('CONFIRM_PRICE', proposal.quoteKey)}>Approve revised total and reserve capacity</button>
      <button className="btn btn-ghost text-red-700" disabled={mutation.isPending}
        onClick={() => decide('REJECT_PRICE', proposal.quoteKey)}>Reject revised price</button>
    </div>}
    {mutation.isPending && <p role="status">Saving request decision...</p>}
  </div>;
}

export function ShipmentRequestInbox() {
  const { user } = useAuth();
  const [status, setStatus] = useState<ShipmentRequest['status']>('PENDING');
  const query = useQuery({ queryKey: ['marketplace', 'shipment-inbox', user?.id],
    queryFn: ShipmentRequestsApi.inbox, refetchInterval: 10_000 });
  const statuses = ['PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED'] as const;
  const visible = query.data?.filter(r => r.status === status);
  return <section className="space-y-4">
    <h2 className="text-2xl font-bold">Targeted shipment inbox</h2>
    <p className="text-sm">Requests expire after one hour. Agents review their own trips; admins review all trips, including traveler trips.
      Travelers remain read-only. Revised prices require sender approval and never extend the deadline.</p>
    <div role="group" aria-label="Shipment request status" className="flex flex-wrap gap-2">
      {statuses.map(value => <button key={value} className={`btn ${value === status ? 'btn-primary' : 'btn-ghost'}`}
        aria-pressed={status === value} onClick={() => setStatus(value)}>{display(value)} ({query.data?.filter(r => r.status === value).length ?? 0})</button>)}
    </div>
    {query.isPending && <p role="status">Loading shipment inbox...</p>}
    {query.isError && <p role="alert">Could not load shipment inbox. <button className="underline" onClick={() => query.refetch()}>Retry inbox</button></p>}
    {visible?.length === 0 && <p className="card">No {status.toLowerCase()} targeted requests.</p>}
    {visible?.map(request => <ShipmentRequestSummary key={request.id} request={request} />)}
  </section>;
}

export function PackageShipmentRequests({ packageId }: { packageId: string }) {
  const { user } = useAuth();
  const query = useQuery({ queryKey: ['marketplace', 'shipment-requests', packageId, user?.id],
    queryFn: () => ShipmentRequestsApi.forPackage(packageId), refetchInterval: 10_000 });
  return <section className="card space-y-4">
    <h2 className="text-xl font-bold">Shipment requests</h2>
    {query.isPending && <p role="status">Loading shipment requests...</p>}
    {query.isError && <p role="alert">Could not load shipment requests. <button className="underline" onClick={() => query.refetch()}>Retry shipment requests</button></p>}
    {query.isSuccess && query.data.length === 0 && <p>No targeted shipment requests to display.</p>}
    {query.data?.map(request => <ShipmentRequestSummary key={request.id} request={request} />)}
  </section>;
}
