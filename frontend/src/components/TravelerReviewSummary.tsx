import { useId } from 'react';
import { Star } from 'lucide-react';

interface Props {
  rating: number | null;
  ratingCount: number;
  compact?: boolean;
}

export function TravelerReviewSummary({ rating, ratingCount, compact = false }: Props) {
  const id = useId();
  const rated = rating !== null && ratingCount > 0;
  const summary = rated
    ? `${rating.toFixed(1)}/5 (${ratingCount} ${ratingCount === 1 ? 'review' : 'reviews'})`
    : 'Not yet rated';
  if (compact) return <span className="inline-flex items-center gap-1">
    <Star className="h-4 w-4 text-amber-600" aria-hidden="true" />{summary}
  </span>;
  return <section aria-labelledby={`${id}-title`} className="rounded-xl border border-emerald-200 bg-emerald-50 p-5 space-y-4">
    <h2 id={`${id}-title`} className="text-xl font-bold">Traveler / agent reviews</h2>
    <div className="flex flex-wrap items-center gap-4">
      <div>
        <p className="text-3xl font-bold text-emerald-900">{rated ? `${rating.toFixed(1)} / 5` : 'Not yet rated'}</p>
        <p className="text-sm">{rated ? `Based on ${ratingCount} ${ratingCount === 1 ? 'sender review' : 'sender reviews'}` : 'No sender reviews yet'}</p>
      </div>
      <div className="relative inline-flex shrink-0 text-amber-600" aria-hidden="true">
        <div className="flex">{[0, 1, 2, 3, 4].map(n => <Star key={n} className="h-6 w-6" />)}</div>
        <div className="absolute inset-y-0 left-0 overflow-hidden" style={{ width: `${rated ? rating / 5 * 100 : 0}%` }}>
          <div className="flex w-max">{[0, 1, 2, 3, 4].map(n => <Star key={n} className="h-6 w-6 fill-current" />)}</div>
        </div>
      </div>
    </div>
    <p className="text-sm">{rated
      ? "Average overall score from sender feedback across this carrier's completed deliveries, not just this trip. Each rated delivery counts once."
      : 'This carrier has not received a sender rating yet. Unrated does not mean a zero-star score.'}</p>
    <p className="text-sm text-ink-600">Earlier overall-only ratings are included. Feedback written by the carrier about a sender does not affect this score.
      Comments, category scores, and reviewer identities remain private to booking participants and administrators.</p>
  </section>;
}
