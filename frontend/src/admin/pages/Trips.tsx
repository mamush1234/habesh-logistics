import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AdminConsoleApi } from '../api';
import {
  AdminBadge, Avatar, EmptyState, ErrorState, LoadingState, PageHeader, Pagination, SearchInput, TableCard, Tabs,
  shortDate, td, th, useListState,
} from '../ui';

export function AdminTrips() {
  const list = useListState();
  const trips = useQuery({
    queryKey: ['admin-console', 'trips', list.query],
    queryFn: () => AdminConsoleApi.trips(list.query),
    placeholderData: previous => previous,
  });
  const counts = trips.data?.counts ?? {};
  const total = Object.values(counts).reduce((sum, n) => sum + n, 0);

  return (
    <>
      <PageHeader title="Trips" subtitle="Traveler trips posted to the marketplace, including trips posted by agents." />
      <div className="card p-0">
        <div className="px-5 pt-3">
          <Tabs label="Trip status" value={list.status || 'ALL'} onChange={v => list.setStatus(v === 'ALL' ? '' : v)} tabs={[
            { value: 'ALL', label: 'All', count: total },
            { value: 'UPCOMING', label: 'Upcoming', count: counts.UPCOMING ?? 0 },
            { value: 'IN_PROGRESS', label: 'In progress', count: counts.IN_PROGRESS ?? 0 },
            { value: 'COMPLETED', label: 'Completed', count: counts.COMPLETED ?? 0 },
          ]} />
        </div>
        <div className="p-5">
          <SearchInput label="Search trips" placeholder="Search traveler or city" value={list.q} onChange={list.setQ} />
        </div>
      </div>
      <div className="mt-4">
        {trips.isLoading && <LoadingState label="Loading trips…" />}
        {trips.isError && <ErrorState error={trips.error} onRetry={() => trips.refetch()} />}
        {trips.data && (trips.data.items.length === 0
          ? <div className="card"><EmptyState title="No trips found" /></div>
          : (
            <TableCard footer={<Pagination page={trips.data.page} totalPages={trips.data.totalPages} onChange={list.setPage} />}>
              <table className="min-w-full divide-y divide-ink-300/60">
                <thead><tr>
                  <th scope="col" className={th}>Traveler</th><th scope="col" className={th}>Route</th>
                  <th scope="col" className={th}>Departure</th><th scope="col" className={th}>Capacity</th>
                  <th scope="col" className={th}>Offers</th><th scope="col" className={th}>Status</th>
                </tr></thead>
                <tbody className="divide-y divide-ink-300/40">
                  {trips.data.items.map(trip => {
                    const used = trip.capacityKg > 0 ? Math.min(100, (Number(trip.reservedKg) / Number(trip.capacityKg)) * 100) : 0;
                    return (
                      <tr key={trip.id}>
                        <td className={td}>
                          <div className="flex items-center gap-3">
                            <Avatar name={trip.travelerName} size="sm" />
                            <div>
                              <div className="font-medium text-ink-900">{trip.travelerName}</div>
                              <div className="text-xs text-ink-500">{trip.travelerEmail}{trip.agentCreated ? ' · via agent' : ''}</div>
                            </div>
                          </div>
                        </td>
                        <td className={td}>{trip.origin} → {trip.destination}</td>
                        <td className={td}>
                          {shortDate(trip.departureDate)}
                          {trip.expectedArrivalDate && <div className="text-xs text-ink-500">Arrives {shortDate(trip.expectedArrivalDate)}</div>}
                        </td>
                        <td className={td}>
                          <div className="text-xs text-ink-500">{trip.reservedKg} / {trip.capacityKg} kg</div>
                          <div className="mt-1 h-1.5 w-24 rounded-full bg-ink-100" role="progressbar" aria-valuemin={0} aria-valuemax={100}
                            aria-valuenow={Math.round(used)} aria-label="Capacity reserved">
                            <div className="h-1.5 rounded-full bg-violet-600" style={{ width: `${used}%` }} />
                          </div>
                        </td>
                        <td className={td}>{trip.offers}</td>
                        <td className={td}><AdminBadge status={trip.status} /></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </TableCard>
          ))}
      </div>
      <p className="mt-4 text-sm text-ink-500">
        Legacy operations trips are managed from the <Link to="/admin/operations" className="underline">operations board</Link>.
      </p>
    </>
  );
}
