import { Star } from 'lucide-react';
import { AdminBadge, money, relativeTime, shortDate, td } from '../ui';
import { DetailsLink, RequestListFrame, RouteCell, SenderCell, statusTabs, useRequestList } from './Requests';

const ALL_STATUSES = ['DRAFT', 'REQUESTED', 'MATCHED', 'PAID_ESCROW', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'DISPUTED'];
const actions = { header: '', className: `${td} text-right`, cell: (r: Parameters<typeof DetailsLink>[0]['r']) => <DetailsLink r={r} /> };

export function AdminPackages() {
  const { list, query } = useRequestList('all');
  return (
    <RequestListFrame title="Packages" subtitle="Every parcel posted on the marketplace, by sender, guest link or agent."
      list={list} query={query} emptyTitle="No packages found" tabs={statusTabs(query.data?.counts ?? {}, ALL_STATUSES)}
      columns={[
        { header: 'Item', cell: r => (
          <div className="max-w-xs">
            <div className="font-medium text-ink-900">{r.itemType.charAt(0) + r.itemType.slice(1).toLowerCase()} · {r.weightKg} kg</div>
            <div className="truncate text-xs text-ink-500" title={r.description}>{r.description}</div>
          </div>
        ) },
        { header: 'Sender', cell: r => <SenderCell r={r} /> },
        { header: 'Route', cell: r => `${r.pickupCity} → ${r.destinationCity}` },
        { header: 'Price', cell: r => money(r.acceptedPrice ?? r.offeredPrice) },
        { header: 'Status', cell: r => <AdminBadge status={r.status} /> },
        { header: 'Created', cell: r => shortDate(r.createdAt) },
        actions,
      ]} />
  );
}

export function AdminBookings() {
  const { list, query } = useRequestList('bookings');
  return (
    <RequestListFrame title="Bookings" subtitle="Requests where the sender accepted a traveler's offer."
      list={list} query={query} emptyTitle="No bookings yet"
      tabs={statusTabs(query.data?.counts ?? {}, ['MATCHED', 'PAID_ESCROW', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'DISPUTED'])}
      columns={[
        { header: 'Route', cell: r => <RouteCell r={r} /> },
        { header: 'Sender', cell: r => <SenderCell r={r} /> },
        { header: 'Traveler', cell: r => r.travelerName ?? '—' },
        { header: 'Agreed price', cell: r => money(r.acceptedPrice) },
        { header: 'Escrow', cell: r => <AdminBadge status={r.escrowStatus} /> },
        { header: 'Status', cell: r => <AdminBadge status={r.status} /> },
        { header: 'Updated', cell: r => relativeTime(r.updatedAt) },
        actions,
      ]} />
  );
}

export function AdminReviews() {
  const { list, query } = useRequestList('reviews');
  return (
    <RequestListFrame title="Reviews" subtitle="Deliveries with feedback from either participant. View a package for both reviews, category scores and comments."
      list={list} query={query} emptyTitle="No reviews yet"
      columns={[
        { header: 'Carrier overall rating', cell: r => r.rating == null ? <span className="text-sm text-ink-500">Sender feedback only; view details</span> : (
          <span className="inline-flex items-center gap-0.5" aria-label={`${r.rating} out of 5 stars`}>
            {[1, 2, 3, 4, 5].map(n => (
              <Star key={n} aria-hidden="true" className={`w-4 h-4 ${n <= (r.rating ?? 0) ? 'fill-amber-400 text-amber-400' : 'text-ink-300'}`} />
            ))}
          </span>
        ) },
        { header: 'Traveler', cell: r => r.travelerName ?? '—' },
        { header: 'Sender', cell: r => <SenderCell r={r} /> },
        { header: 'Route', cell: r => `${r.pickupCity} → ${r.destinationCity}` },
        { header: 'Updated', cell: r => shortDate(r.updatedAt) },
        actions,
      ]} />
  );
}
