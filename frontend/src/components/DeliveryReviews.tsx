import { useId, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { ReviewsApi, carrierReviewCriteria, senderReviewCriteria } from '../api/reviews';
import type { ReviewBooking, ReviewCriterion, ReviewDirection, ReviewInbox, ReviewInput } from '../api/reviews';
import { useAuth } from '../auth/AuthContext';

export const reviewLabels: Record<ReviewCriterion, string> = {
  COMMUNICATION: 'Communication', ON_TIME_DELIVERY: 'On-time delivery', PACKAGE_HANDLING: 'Package handling',
  RELIABILITY: 'Reliability', ACCURATE_DESCRIPTION: 'Accurate description', HANDOFF_TIMELINESS: 'Handoff timeliness',
  PACKAGING_QUALITY: 'Packaging quality',
};
function criteria(direction: ReviewDirection) {
  return direction === 'SENDER_TO_CARRIER' ? carrierReviewCriteria : senderReviewCriteria;
}
function reviewError(error: unknown) {
  return isAxiosError<{ message?: string }>(error)
    ? error.response?.data?.message || 'Could not save your review. Please retry.'
    : 'Could not save your review. Please retry.';
}

export function PackageReviews({ packageId }: { packageId: string }) {
  const { user } = useAuth();
  const query = useQuery({ queryKey: ['delivery-reviews', 'package', packageId, user?.id],
    queryFn: () => ReviewsApi.forPackage(packageId), refetchInterval: 15000 });
  return <section className="card space-y-4">
    <h2 className="text-xl font-bold">Delivery reviews</h2>
    {query.isPending && <p role="status">Loading delivery reviews...</p>}
    {query.isError && <p role="alert">Could not load delivery reviews. <button className="underline" onClick={() => query.refetch()}>Retry reviews</button></p>}
    {query.data && <ReviewBookingPanel booking={query.data} />}
  </section>;
}

export function ReviewBookingPanel({ booking, showRoute = false }: { booking: ReviewBooking; showRoute?: boolean }) {
  return <div className="space-y-4 min-w-0">
    {showRoute && <h2 className="text-xl font-bold break-words">{booking.origin} &rarr; {booking.destination}</h2>}
    <p className="text-sm text-ink-500">Reviews are shared only with the booking participants and administrators.
      Public traveler ratings show the overall score and count, not comments or identities.</p>
    {booking.reviews.length === 0 && <p>No reviews for this delivery yet.</p>}
    {booking.reviews.map(review => <article key={review.id} className="rounded-xl border border-ink-300 p-4 space-y-3">
      <div className="flex flex-wrap justify-between gap-2">
        <h3 className="font-bold">{review.direction === 'SENDER_TO_CARRIER' ? 'Sender review of traveler / agent' : 'Traveler / agent review of sender'}</h3>
        <span className="rounded-full bg-emerald-100 px-3 py-1 text-sm font-bold text-emerald-900">Overall: {review.rating} / 5</span>
      </div>
      {review.mine && <p className="text-sm font-semibold">Your review</p>}
      {review.legacy ? <p className="text-sm">Earlier overall-only rating. Category scores and a comment were not collected.</p>
        : <p className="text-sm text-ink-500">{new Date(review.createdAt).toLocaleString()}</p>}
      <dl className="grid gap-2 sm:grid-cols-2">{criteria(review.direction).map(key => {
        const score = review.scores[key];
        return score == null ? null : <div key={key} className="flex justify-between gap-3 text-sm">
          <dt>{reviewLabels[key]}</dt><dd>{score} / 5</dd>
        </div>;
      })}</dl>
      {review.comment && <p className="whitespace-pre-wrap break-words">{review.comment}</p>}
    </article>)}
    {booking.canReview && booking.direction && <ReviewForm key={booking.packageId} booking={booking} direction={booking.direction} />}
    {!booking.canReview && booking.status !== 'COMPLETED' && <p role="note">Reviews open after delivery is confirmed as completed.</p>}
    {!booking.canReview && booking.reviews.some(r => r.mine) && <p role="status">Your review has been saved. Reviews can be submitted only once per delivery.</p>}
    {showRoute && <Link className="underline" to={`/my-reviews/${booking.packageId}`}>Open delivery reviews</Link>}
  </div>;
}

function ReviewForm({ booking, direction }: { booking: ReviewBooking; direction: ReviewDirection }) {
  const id = useId();
  const { user } = useAuth();
  const qc = useQueryClient();
  const [overall, setOverall] = useState('');
  const [scores, setScores] = useState<Partial<Record<ReviewCriterion, number>>>({});
  const [comment, setComment] = useState('');
  const mutation = useMutation({
    mutationFn: (input: ReviewInput) => ReviewsApi.submit(booking.packageId, input),
    onSuccess: updated => {
      qc.setQueryData(['delivery-reviews', 'package', booking.packageId, user?.id], updated);
      qc.setQueriesData<ReviewInbox>({ queryKey: ['delivery-reviews', 'inbox', user?.id] }, previous => previous
        ? { ...previous, items: previous.items.map(item => item.packageId === updated.packageId ? updated : item) }
        : previous);
      qc.invalidateQueries({ queryKey: ['delivery-reviews'] });
      qc.invalidateQueries({ queryKey: ['marketplace'] });
      qc.invalidateQueries({ queryKey: ['public-travel'] });
      qc.invalidateQueries({ queryKey: ['admin-console'] });
      toast.success('Review saved');
    },
    onError: () => qc.invalidateQueries({ queryKey: ['delivery-reviews'] }),
  });
  const target = direction === 'SENDER_TO_CARRIER' ? 'traveler / agent' : 'sender';
  return <form className="rounded-xl bg-emerald-50 p-4 space-y-4" onSubmit={event => {
    event.preventDefault();
    mutation.mutate({ rating: Number(overall), scores, comment: comment.trim() || undefined });
  }}>
    <h3 className="font-bold">Review your {target}</h3>
    <p className="text-sm">Choose an overall score and all four category scores (1 = poor, 5 = excellent).
      Your feedback cannot be edited or submitted again.</p>
    <fieldset disabled={mutation.isPending} className="space-y-4">
      <legend className="sr-only">Rating scores for your {target}</legend>
      <label className="label" htmlFor={`${id}-overall`}>Overall rating
        <select id={`${id}-overall`} className="input" required value={overall} onChange={event => setOverall(event.target.value)}>
          <option value="">Choose a score</option>{[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n} / 5</option>)}
        </select>
      </label>
      <div className="grid gap-4 sm:grid-cols-2">{criteria(direction).map(key => <label key={key} className="label" htmlFor={`${id}-${key}`}>
        {reviewLabels[key]}
        <select id={`${id}-${key}`} className="input" required value={scores[key] ?? ''}
          onChange={event => setScores(previous => ({ ...previous, [key]: Number(event.target.value) }))}>
          <option value="">Choose a score</option>{[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n} / 5</option>)}
        </select>
      </label>)}</div>
      <label className="label" htmlFor={`${id}-comment`}>Comment (optional)
        <textarea id={`${id}-comment`} className="input" rows={4} maxLength={2000}
          value={comment} onChange={event => setComment(event.target.value)} />
      </label>
      <p className="text-sm text-ink-500">Keep feedback about this delivery. Do not include phone numbers, addresses or other personal information.</p>
      <button className="btn btn-primary" type="submit">{mutation.isPending ? 'Saving review...' : 'Submit review'}</button>
    </fieldset>
    {mutation.isPending && <p role="status">Saving your review...</p>}
    {mutation.isError && <p role="alert">{reviewError(mutation.error)}</p>}
  </form>;
}
