import { useState } from 'react';
import { TripDetailsSummary } from '../../components/MarketTripFields';
import { ShipmentRequestInbox } from '../../components/ShipmentRequestSummary';
import { Link } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { clsx } from 'clsx';
import { ArrowRight, PackagePlus, Plane, Send } from 'lucide-react';
import { AgentApi } from '../api';
import type { AgentPackage } from '../types';
import { deliveryStatuses, type DeliveryStatus } from '../../api/marketplace';
import {
  Avatar, EmptyState, ErrorState, errorMessage, greenButton, LoadingState, money, PageHeader, Pagination, shortDate,
  StatusBadge, titleCase,
} from '../ui';

function SubmitDraftButton({ pkg }: { pkg: AgentPackage }) {
  const qc = useQueryClient();
  const submit = useMutation({
    mutationFn: () => AgentApi.submitDraft(pkg.customerId, pkg.id),
    onSuccess: () => { toast.success('Draft submitted for admin review'); qc.invalidateQueries({ queryKey: ['agent'] }); },
    onError: error => toast.error(errorMessage(error)),
  });
  return (
    <button type="button" className={clsx(greenButton, '!py-1 !px-3 text-xs')} disabled={submit.isPending}
      onClick={() => submit.mutate()} aria-label={`Submit draft ${pkg.pickupCity} to ${pkg.destinationCity}`}>
      <Send className="w-3.5 h-3.5" aria-hidden="true" /> {submit.isPending ? 'Submitting…' : 'Submit'}
    </button>
  );
}

function PackageTable({ items, dim, showSubmit }: { items: AgentPackage[]; dim?: boolean; showSubmit?: boolean }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-ink-500 border-b border-ink-100">
            <th scope="col" className="py-2 pr-3 font-medium">Route</th>
            <th scope="col" className="py-2 pr-3 font-medium">Customer</th>
            <th scope="col" className="py-2 pr-3 font-medium hidden md:table-cell">Item</th>
            <th scope="col" className="py-2 pr-3 font-medium hidden md:table-cell">Deliver by</th>
            <th scope="col" className="py-2 pr-3 font-medium hidden sm:table-cell">Price</th>
            <th scope="col" className="py-2 pr-3 font-medium">Status</th>
            <th scope="col" className="py-2 font-medium"><span className="sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody className={clsx(dim && 'opacity-60')}>
          {items.map(p => (
            <tr key={p.id} className="border-b border-ink-100 last:border-0">
              <td className="py-3 pr-3 font-semibold">{p.pickupCity} → {p.destinationCity}</td>
              <td className="py-3 pr-3"><span className="flex items-center gap-2"><Avatar name={p.customerName} size="sm" />{p.customerName}</span></td>
              <td className="py-3 pr-3 hidden md:table-cell">{p.items && p.items.length > 1 ? `${p.items.length} items` : titleCase(p.itemType)} · {p.weightKg} kg</td>
              <td className="py-3 pr-3 hidden md:table-cell">{shortDate(p.deadline)}</td>
              <td className="py-3 pr-3 hidden sm:table-cell">{money(p.offeredPrice)}</td>
              <td className="py-3 pr-3">
                <span className="flex flex-wrap gap-1">
                  <StatusBadge status={p.status} />
                  {p.status === 'REQUESTED' && <StatusBadge status={p.adminApproved ? 'ACTIVE' : 'PENDING'} label={p.adminApproved ? 'Approved' : 'Awaiting admin'} />}
                </span>
              </td>
              <td className="py-3 text-right whitespace-nowrap">
                {showSubmit && p.status === 'DRAFT'
                  ? <SubmitDraftButton pkg={p} />
                  : p.status !== 'DRAFT' && (
                    <Link to={`/marketplace/requests/${p.id}`} className="btn btn-ghost !py-1 !px-3 text-xs" aria-label={`Open ${p.pickupCity} to ${p.destinationCity}`}>
                      Open <ArrowRight className="w-3.5 h-3.5" aria-hidden="true" />
                    </Link>
                  )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function ShipmentRequestsPage() {
  const [status, setStatus] = useState<DeliveryStatus | ''>('');
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['agent', 'packages', status, page],
    queryFn: () => AgentApi.packages(status || undefined, page),
    placeholderData: keepPreviousData,
  });
  return (
    <>
      <ShipmentRequestInbox />
      <PageHeader title="Shipment Requests" subtitle="Packages you created for your senders, including drafts."
        actions={<Link to="/agent/packages/new" className={greenButton}><PackagePlus className="w-4 h-4" aria-hidden="true" /> Create Package</Link>} />
      <section className="card">
        <div className="flex justify-end mb-4">
          <label className="flex items-center gap-2 text-sm">
            <span>Status</span>
            <select className="input !w-auto" value={status} onChange={e => { setStatus(e.target.value as DeliveryStatus | ''); setPage(0); }}>
              <option value="">All</option>
              {deliveryStatuses.map(s => <option key={s} value={s}>{titleCase(s)}</option>)}
            </select>
          </label>
        </div>
        {query.isPending ? <LoadingState label="Loading requests…" />
          : query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} />
          : query.data.items.length === 0 ? (
            <EmptyState title={status ? `No ${titleCase(status).toLowerCase()} requests` : 'No shipment requests yet'}
              action={!status && <Link to="/agent/packages/new" className={greenButton}>Create Package</Link>} />
          ) : (
            <>
              <PackageTable items={query.data.items} dim={query.isPlaceholderData} showSubmit />
              <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={setPage} />
            </>
          )}
      </section>
    </>
  );
}

const inFlight: DeliveryStatus[] = ['MATCHED', 'PAID_ESCROW', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED', 'DELIVERED'];

export function TrackingPage() {
  const [page, setPage] = useState(0);
  const [tripPage, setTripPage] = useState(0);
  const packages = useQuery({
    queryKey: ['agent', 'packages', 'tracking', page],
    queryFn: () => AgentApi.packages(undefined, page),
    placeholderData: keepPreviousData,
  });
  const trips = useQuery({ queryKey: ['agent', 'trips', tripPage], queryFn: () => AgentApi.trips(tripPage), placeholderData: keepPreviousData });
  const tracked = packages.data?.items.filter(p => p.status !== 'DRAFT') ?? [];

  return (
    <>
      <PageHeader title="Tracking" subtitle="Follow your customers' packages and trips." />
      <section className="card mb-6" aria-labelledby="tracking-packages">
        <h2 id="tracking-packages" className="text-lg font-semibold mb-2">Packages</h2>
        {packages.isPending ? <LoadingState label="Loading packages…" />
          : packages.isError ? <ErrorState error={packages.error} onRetry={() => packages.refetch()} />
          : tracked.length === 0 ? <EmptyState title="Nothing to track yet">Submitted packages appear here.</EmptyState>
          : (
            <>
              <ul className="grid gap-3 md:grid-cols-2">
                {tracked.map(p => {
                  const stage = p.status === 'COMPLETED' ? 6 : inFlight.indexOf(p.status) + 1;
                  return (
                    <li key={p.id} className="rounded-2xl ring-1 ring-ink-300/60 p-4">
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <div className="font-semibold">{p.pickupCity} → {p.destinationCity}</div>
                          <div className="text-xs text-ink-500">{p.customerName} · deliver by {shortDate(p.deadline)}</div>
                        </div>
                        <StatusBadge status={p.status} />
                      </div>
                      {stage > 0 && (
                        <div className="mt-3 h-2 rounded-full bg-ink-100" role="progressbar" aria-label="Delivery progress"
                          aria-valuemin={0} aria-valuemax={6} aria-valuenow={stage}>
                          <div className="h-2 rounded-full bg-emerald-600" style={{ width: `${(stage / 6) * 100}%` }} />
                        </div>
                      )}
                      <Link to={`/marketplace/requests/${p.id}`} className="inline-flex items-center gap-1 text-sm text-emerald-700 hover:underline mt-3">
                        View timeline <ArrowRight className="w-3.5 h-3.5" aria-hidden="true" />
                      </Link>
                    </li>
                  );
                })}
              </ul>
              <Pagination page={packages.data.page} totalPages={packages.data.totalPages} onChange={setPage} />
            </>
          )}
      </section>

      <section className="card" aria-labelledby="tracking-trips">
        <h2 id="tracking-trips" className="text-lg font-semibold mb-2">Trips</h2>
        {trips.isPending ? <LoadingState label="Loading trips…" />
          : trips.isError ? <ErrorState error={trips.error} onRetry={() => trips.refetch()} />
          : trips.data.items.length === 0 ? (
            <EmptyState title="No trips posted yet" action={<Link to="/agent/trips/new" className={greenButton}>Post Trip</Link>} />
          ) : (
            <>
              <ul className="divide-y divide-ink-100">
                {trips.data.items.map(t => (
                  <li key={t.id} className="flex flex-wrap items-center gap-3 py-3">
                    <Plane className="w-5 h-5 text-emerald-700" aria-hidden="true" />
                    <div className="min-w-0 flex-1">
                      <div className="font-semibold">{t.origin} → {t.destination}</div>
                      <div className="text-xs text-ink-500">{t.customerName} · departs {shortDate(t.departureDate)}</div>
                      <TripDetailsSummary trip={t} />
                    </div>
                    <span className="text-sm">{t.availableKg} / {t.capacityKg} kg free</span>
                  </li>
                ))}
              </ul>
              <Pagination page={trips.data.page} totalPages={trips.data.totalPages} onChange={setTripPage} />
            </>
          )}
      </section>
    </>
  );
}
