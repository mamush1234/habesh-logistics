import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ReviewsApi } from '../api/reviews';
import { useAuth } from '../auth/AuthContext';
import { PackageReviews, ReviewBookingPanel } from '../components/DeliveryReviews';

export function DeliveryReviewInbox() {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const query = useQuery({ queryKey: ['delivery-reviews', 'inbox', user?.id, page],
    queryFn: () => ReviewsApi.inbox(page), refetchInterval: 15000 });
  return <div className="max-w-4xl mx-auto space-y-5">
    <h1 className="text-3xl font-bold">My delivery reviews</h1>
    <p>Review your completed deliveries and read feedback from the other participant.</p>
    {user?.role === 'TRAVELER' && <p role="note" className="rounded-xl bg-amber-50 p-4">
      Travelers can submit post-delivery reviews here. Offers, payments, delivery status changes and sending chat messages remain disabled.
    </p>}
    {query.isPending && <p role="status">Loading completed deliveries...</p>}
    {query.isError && <p role="alert">Could not load your delivery reviews. <button className="underline" onClick={() => query.refetch()}>Retry delivery reviews</button></p>}
    {query.data?.items.length === 0 && <p className="card">No completed deliveries to review yet. Reviews open after delivery completion is confirmed.</p>}
    {query.data?.items.map(booking => <section key={booking.packageId} className="card">
      <ReviewBookingPanel booking={booking} showRoute />
    </section>)}
    {query.data && query.data.totalPages > 1 && <nav aria-label="Review pages" className="flex flex-wrap items-center gap-3">
      <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(n => n - 1)}>Previous reviews</button>
      <span>Page {page + 1} of {query.data.totalPages}</span>
      <button className="btn btn-ghost" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage(n => n + 1)}>Next reviews</button>
    </nav>}
  </div>;
}

export function DeliveryReviewDetails() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  return <div className="max-w-4xl mx-auto space-y-5">
    <Link className="underline" to={user?.role === 'ADMIN' ? '/admin/reviews' : '/my-reviews'}>Back to my reviews</Link>
    <h1 className="text-3xl font-bold">Delivery reviews</h1>
    <PackageReviews packageId={id} />
  </div>;
}
