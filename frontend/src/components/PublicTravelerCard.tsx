import { Link } from 'react-router-dom';
import { ShieldCheck, UserRound } from 'lucide-react';
import type { PublicTravel } from '../types';
import { TravelerReviewSummary } from './TravelerReviewSummary';

export function PublicTripSummary({ trip }: { trip: PublicTravel }) {
  const verified = trip.verification !== 'UNVERIFIED';
  return <>
    <div className="flex items-center gap-3">
      <span className="h-14 w-14 shrink-0 rounded-full bg-emerald-50 text-emerald-800 grid place-items-center" aria-hidden="true">
        <UserRound className="h-7 w-7" />
      </span>
      <div className="min-w-0 space-y-1">
        <h2 className="text-xl font-bold break-words">{trip.origin} &rarr; {trip.destination}</h2>
        <p className="text-sm text-ink-500">Private traveler / agent</p>
      </div>
    </div>
    <div className="flex flex-wrap gap-3 items-center text-sm">
      <span className={`inline-flex items-center gap-1 rounded-full px-3 py-1 font-semibold ${verified
        ? 'bg-emerald-100 text-emerald-900' : 'bg-amber-50 text-amber-900'}`}>
        {verified && <ShieldCheck className="h-4 w-4" aria-hidden="true" />}
        {trip.verification === 'FULLY_VERIFIED' ? 'Fully verified' : verified ? 'ID verified' : 'Unverified'}
      </span>
      <TravelerReviewSummary rating={trip.rating} ratingCount={trip.ratingCount} compact />
    </div>
    <p>Departs {trip.departureDate}{trip.expectedArrivalDate && <> &middot; Arrives {trip.expectedArrivalDate}</>}</p>
    {!trip.expectedArrivalDate && <p className="text-sm text-ink-500">Arrival date not provided.</p>}
    <p className="font-semibold text-emerald-800">{trip.availableCapacityKg} kg available</p>
    <div>
      <h3 className="font-semibold">Accepted items and prices</h3>
      <ul className="mt-2 space-y-1 text-sm">{trip.allowedItems.map(category => {
        const price = trip.itemPrices.find(p => p.category === category);
        return <li key={category} className="flex flex-wrap justify-between gap-x-3">
          <span>{category.replaceAll('_', ' ')}</span>
          <span>{price ? `$${price.price.toFixed(2)} ${price.unit === 'PER_KG' ? 'per kg' : 'per item'}` : 'Price not provided'}</span>
        </li>;
      })}</ul>
      {trip.itemPrices.length > 0 && <p className="mt-2 text-xs text-ink-500">Advertised USD prices before platform fees. No booking or price agreement is created here.</p>}
    </div>
  </>;
}

export function PublicTravelerCard({ trip }: { trip: PublicTravel }) {
  return <article className="card space-y-4 min-w-0">
    <PublicTripSummary trip={trip} />
    <Link className="btn bg-emerald-700 text-white hover:bg-emerald-800"
      to={`/browse-travelers/${encodeURIComponent(trip.id)}`}>View trip</Link>
  </article>;
}
