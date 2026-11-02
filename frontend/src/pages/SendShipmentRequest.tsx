import { useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { z } from 'zod';
import { useAuth } from '../auth/AuthContext';
import { PublicApi } from '../api/endpoints';
import { MarketplaceApi } from '../api/marketplace';
import { ShipmentRequestsApi } from '../api/shipmentRequests';
import type { ShipmentRequest } from '../api/shipmentRequests';
import { PublicTripSummary } from '../components/PublicTravelerCard';
import { PackageContentsSummary } from '../components/PackageItemsEditor';
import { ShipmentPriceSummary, ShipmentRequestSummary } from '../components/ShipmentRequestSummary';
import { DemoNotice, marketError } from './Marketplace';

function requestError(error: unknown) {
  return isAxiosError<{ message?: string }>(error) && error.response?.data?.message
    ? error.response.data.message : 'Could not load the price. Please retry.';
}

export function SendShipmentRequest() {
  const { tripId = '' } = useParams();
  const [params] = useSearchParams();
  const packageId = params.get('package') || '';
  return <ShipmentRequestWizard key={`${tripId}:${packageId}`} tripId={tripId} initialPackageId={packageId} />;
}

function ShipmentRequestWizard({ tripId, initialPackageId }: { tripId: string; initialPackageId: string }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [packageId, setPackageId] = useState(initialPackageId);
  const [step, setStep] = useState(0);
  const [message, setMessage] = useState('');
  const [sent, setSent] = useState<ShipmentRequest | null>(null);
  const stepHeading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { if (step > 0) stepHeading.current?.focus(); }, [step]);
  const validId = z.string().uuid().safeParse(tripId).success;
  const trip = useQuery({ queryKey: ['public-travel', tripId], queryFn: () => PublicApi.trip(tripId), enabled: validId });
  const packages = useQuery({ queryKey: ['marketplace', 'requests', user?.id], queryFn: MarketplaceApi.requests, enabled: validId });
  const eligible = packages.data?.filter(p => p.senderId === user?.id && p.status === 'REQUESTED' && !p.guestRequest && !p.convertedShipmentId) ?? [];
  const selected = eligible.find(p => p.id === packageId);
  const quote = useQuery({ queryKey: ['marketplace', 'shipment-quote', packageId, tripId, user?.id],
    queryFn: () => ShipmentRequestsApi.quote(packageId, tripId), enabled: step === 1 && !!selected && !!trip.data,
    staleTime: 0, retry: false });
  const history = useQuery({ queryKey: ['marketplace', 'shipment-requests', packageId, user?.id],
    queryFn: () => ShipmentRequestsApi.forPackage(packageId), enabled: !!sent, refetchInterval: 10_000 });
  const receipt = history.data?.find(request => request.id === sent?.id) ?? sent;
  const mutation = useMutation({
    mutationFn: () => {
      if (!quote.data) throw new Error('Review a valid price before submitting');
      return ShipmentRequestsApi.submit({ packageId, tripId, quoteKey: quote.data.quoteKey, message: message.trim() || undefined });
    },
    onSuccess: request => { setSent(request); setStep(2); qc.invalidateQueries({ queryKey: ['marketplace'] }); },
    onError: error => { marketError(error); qc.invalidateQueries({ queryKey: ['marketplace', 'shipment-quote', packageId, tripId, user?.id] }); },
  });
  return <div className="max-w-3xl mx-auto space-y-6">
    <Link className="underline" to={`/browse-travelers/${encodeURIComponent(tripId)}`}>Back to trip details</Link>
    <h1 className="text-3xl font-bold">Request shipment</h1>
    <ol aria-label="Shipment request steps" className="flex flex-wrap gap-3 text-sm">
      {['Package', 'Price & Confirm', 'Request Sent'].map((label, index) => <li key={label} aria-current={step === index ? 'step' : undefined}
        className={`rounded-full px-4 py-2 font-semibold ${step === index ? 'bg-emerald-700 text-white' : 'bg-ink-100 text-ink-700'}`}>{index + 1}. {label}</li>)}
    </ol>
    <DemoNotice />
    {!validId ? <p role="alert">Invalid trip. Choose a trip from Browse Travelers.</p>
      : sent && receipt ? <section className="space-y-4">
        <h2 className="text-2xl font-bold" tabIndex={-1} ref={stepHeading}>Request sent</h2>
        {history.isError && <p role="alert">Could not refresh request status. <button className="underline" onClick={() => history.refetch()}>Retry status</button></p>}
        <ShipmentRequestSummary request={receipt} />
        <Link className="btn btn-primary" to={`/marketplace/requests/${sent.packageId}`}>View package and request status</Link>
      </section> : <>
        {trip.isPending && <p role="status">Loading selected trip...</p>}
        {trip.isError && <p role="alert">This trip could not be loaded or is no longer available. <button className="underline" onClick={() => trip.refetch()}>Retry trip</button></p>}
        {trip.isSuccess && <section className="card space-y-4"><h2 className="text-xl font-bold">Selected traveler / agent trip</h2><PublicTripSummary trip={trip.data} /></section>}
        {step === 0 ? <form className="card space-y-4" onSubmit={e => { e.preventDefault(); if (selected && trip.isSuccess) setStep(1); }}>
          <h2 className="text-xl font-bold">Select your package</h2>
          {packages.isPending && <p role="status">Loading your packages...</p>}
          {packages.isError && <p role="alert">Could not load your packages. <button type="button" className="underline" onClick={() => packages.refetch()}>Retry packages</button></p>}
          {packages.isSuccess && eligible.length === 0 && <p>No open packages available. <Link className="underline" to={`/send?travel=${encodeURIComponent(tripId)}`}>Post a package first</Link>, then return here.</p>}
          <label className="label">Package<select className="input" required value={selected?.id ?? ''} onChange={e => { setPackageId(e.target.value); mutation.reset(); }}>
            <option value="">Select a package</option>
            {eligible.map(p => <option key={p.id} value={p.id}>{p.pickupCity} to {p.destinationCity} - {p.weightKg} kg - {p.description.slice(0, 60)}</option>)}
          </select></label>
          {selected && <div className="space-y-2"><p className="text-sm">Available {selected.availableFrom} &middot; Deadline {selected.deadline}</p>
            <PackageContentsSummary items={selected.items ?? []} receiver={selected.receiver} />
            <Link className="underline text-sm" to={`/marketplace/requests/${selected.id}`}>View package and existing requests</Link></div>}
          <p className="text-sm">All categories need advertised prices. Route, dates, capacity, and package ownership are checked by the server.
            Travelers remain read-only; administrators review requests for traveler trips.</p>
          <button className="btn btn-primary" disabled={!selected || !trip.isSuccess}>Continue to price</button>
        </form> : <section className="card space-y-4">
          <h2 className="text-xl font-bold" tabIndex={-1} ref={stepHeading}>Price &amp; Confirm</h2>
          {quote.isFetching && <p role="status">Checking current prices and fees...</p>}
          {quote.isError && <p role="alert">{requestError(quote.error)} <button className="underline" onClick={() => quote.refetch()}>Retry price</button></p>}
          {quote.isSuccess && <ShipmentPriceSummary quote={quote.data} />}
          <form className="space-y-4" onSubmit={e => { e.preventDefault(); if (quote.isSuccess && !quote.isFetching && selected && trip.isSuccess) mutation.mutate(); }}>
            <label className="label">Optional message<textarea className="input" maxLength={500} value={message} disabled={mutation.isPending} onChange={e => setMessage(e.target.value)} /></label>
            <p className="text-sm">The request expires in one hour. No capacity is reserved and payment is not available until acceptance.
              Admins review traveler trips; agents review their own trips. Do not include contact details in your message.</p>
            {mutation.isError && <p role="alert">{requestError(mutation.error)}</p>}
            <div className="flex flex-wrap justify-between gap-3">
              <button type="button" className="btn btn-ghost" disabled={mutation.isPending} onClick={() => { setStep(0); mutation.reset(); }}>Back to package</button>
              <button className="btn btn-primary" disabled={!quote.isSuccess || quote.isFetching || !selected || !trip.isSuccess || mutation.isPending}>
                {mutation.isPending ? 'Sending request...' : 'Send shipment request'}
              </button>
            </div>
          </form>
        </section>}
      </>}
  </div>;
}
