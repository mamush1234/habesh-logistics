import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { z } from 'zod';
import { PublicApi } from '../api/endpoints';
import { MarketplaceApi } from '../api/marketplace';
import { useAuth } from '../auth/AuthContext';
import { PublicTripSummary } from '../components/PublicTravelerCard';
import { TravelerReviewSummary } from '../components/TravelerReviewSummary';
import { DemoNotice } from './Marketplace';

export function TravelerTripDetails() {
  const { id = '' } = useParams();
  const { user, initializing } = useAuth();
  const validId = z.string().uuid().safeParse(id).success;
  const trip = useQuery({
    queryKey: ['public-travel', id], queryFn: () => PublicApi.trip(id), enabled: validId,
    refetchInterval: 60_000,
    retry: (count, error) => !(isAxiosError(error) && error.response?.status === 404) && count < 2,
  });
  const canViewDetails = !initializing && (user?.role === 'SENDER' || user?.role === 'ADMIN');
  const details = useQuery({
    queryKey: ['marketplace', 'trip', id, user?.id], queryFn: () => MarketplaceApi.trip(id),
    enabled: validId && canViewDetails && trip.isSuccess, refetchInterval: 60_000,
  });
  const unavailable = !validId || (isAxiosError(trip.error) && trip.error.response?.status === 404);
  const returnTo = `/browse-travelers/${encodeURIComponent(id)}`;
  return <div className="max-w-3xl mx-auto space-y-6">
    <Link className="underline" to="/browse-travelers">Back to Browse Travelers</Link>
    <h1 className="text-3xl font-bold">Trip details</h1>
    {unavailable ? <p role="alert" className="card">This trip is unavailable. It may be full, no longer upcoming, or no longer listed.</p>
      : trip.isPending ? <p role="status">Loading trip details...</p>
        : trip.isError ? <p role="alert">Could not load this trip. <button className="underline" onClick={() => trip.refetch()}>Try again</button></p>
          : <article className="card space-y-5 min-w-0">
            <PublicTripSummary trip={trip.data} />
            <TravelerReviewSummary rating={trip.data.rating} ratingCount={trip.data.ratingCount} />
            <section className="space-y-2 text-sm">
              <h2 className="text-lg font-bold">About this trip</h2>
              <p>Posted {new Date(trip.data.createdAt).toLocaleDateString()}. Verification is administrator-assigned.
                Ratings come from completed deliveries.</p>
              <p>Names, photos, and contact details remain private. No public traveler biography is provided.</p>
            </section>
            <section className="space-y-2 text-sm">
              <h2 className="text-lg font-bold">Handoff and trip notes</h2>
              {initializing ? <p role="status">Checking your session...</p>
                : canViewDetails ? <>
                  {details.isPending && <p role="status">Loading handoff details...</p>}
                  {details.isError && <p role="alert">Could not load handoff details. <button className="underline" onClick={() => details.refetch()}>Retry handoff details</button></p>}
                  {details.isSuccess && <>
                    <p className="whitespace-pre-wrap break-words"><strong>Preferred handoff: </strong>{details.data.handoffLocation || 'Not provided'}</p>
                    <p className="whitespace-pre-wrap break-words"><strong>Trip notes: </strong>{details.data.notes || 'Not provided'}</p>
                    <p>Will not carry: {details.data.blockedItems.map(category => category.replaceAll('_', ' ')).join(', ') || 'None specified'}</p>
                  </>}
                </> : !user ? <p>Handoff details and notes are private. <Link className="underline" to={`/login?next=${encodeURIComponent(returnTo)}`}>Sign in as a sender to view them.</Link></p>
                  : <p>Use your own trip management view for handoff details and notes. Other carriers' private trip details are not shown here.</p>}
            </section>
            <section className="space-y-3 text-sm">
              <h2 className="text-lg font-bold">Send a package on this route</h2>
              <p>Select one of your open packages, review the advertised price and platform fee, and send a request for this trip.
                Requests expire in one hour and do not reserve capacity. Admins review traveler-trip requests while travelers remain read-only.</p>
              {!user || user.role === 'SENDER'
                ? <div className="flex flex-wrap gap-3">
                  <Link className="btn bg-emerald-700 text-white hover:bg-emerald-800" to={`/shipment-request/${encodeURIComponent(id)}`}>Request shipment</Link>
                  <Link className="btn btn-ghost" to={`/send?travel=${encodeURIComponent(id)}`}>Post a package for this route</Link>
                </div>
                : <Link className="btn btn-ghost" to="/marketplace">Open marketplace</Link>}
            </section>
          </article>}
    <DemoNotice />
  </div>;
}
