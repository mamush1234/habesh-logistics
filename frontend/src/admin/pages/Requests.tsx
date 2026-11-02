import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import type { ReactNode } from 'react';
import { ShipmentRequestInbox } from '../../components/ShipmentRequestSummary';
import { MarketplaceApi } from '../../api/marketplace';
import { AdminConsoleApi } from '../api';
import type { AdminRequestRow, ListPage, RequestView } from '../types';
import {
  AdminBadge, EmptyState, ErrorState, LoadingState, PageHeader, Pagination, SearchInput, TableCard, Tabs,
  errorMessage, money, purpleButton, relativeTime, shortDate, td, th, useListState, type TabItem,
} from '../ui';

export function useRequestList(view: RequestView, initialStatus = '') {
  const list = useListState(initialStatus);
  const query = useQuery({
    queryKey: ['admin-console', 'requests', view, list.query],
    queryFn: () => AdminConsoleApi.requests(view, list.query),
    placeholderData: previous => previous,
  });
  return { list, query };
}

export function statusTabs(counts: Record<string, number>, statuses: string[], extra: TabItem[] = []): TabItem[] {
  const all = Object.entries(counts).filter(([k]) => k !== 'AWAITING_REVIEW').reduce((s, [, n]) => s + n, 0);
  return [{ value: '', label: 'All', count: all }, ...extra,
    ...statuses.map(s => ({ value: s, label: s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' '), count: counts[s] ?? 0 }))];
}

export const SenderCell = ({ r }: { r: AdminRequestRow }) => (
  <div>
    <div className="font-medium text-ink-900">{r.senderName ?? 'Unknown sender'}</div>
    <div className="text-xs text-ink-500">
      {r.senderEmail}{r.guest ? ' · guest' : ''}{r.agentCreated ? ' · via agent' : ''}
    </div>
  </div>
);
export const RouteCell = ({ r }: { r: AdminRequestRow }) => (
  <div>
    <div>{r.pickupCity} → {r.destinationCity}</div>
    <div className="text-xs text-ink-500">{r.itemType.toLowerCase()} · {r.weightKg} kg</div>
  </div>
);
export const DetailsLink = ({ r }: { r: AdminRequestRow }) => (
  <Link to={`/marketplace/requests/${r.id}`} className="btn btn-ghost" aria-label={`View request ${r.pickupCity} to ${r.destinationCity}`}>View</Link>
);

export interface Column { header: string; cell: (r: AdminRequestRow) => ReactNode; className?: string }

/** Shared page frame for every request-backed admin view (tabs, search, table, pagination, states). */
export function RequestListFrame({ title, subtitle, tabs, list, query, columns, emptyTitle }: {
  title: string; subtitle: string; tabs?: TabItem[]; list: ReturnType<typeof useListState>;
  query: UseQueryResult<ListPage<AdminRequestRow>>; columns: Column[]; emptyTitle: string;
}) {
  return (
    <>
      <PageHeader title={title} subtitle={subtitle} />
      <div className="card p-0">
        {tabs && (
          <div className="px-5 pt-3">
            <Tabs label={`${title} status`} value={list.status} onChange={list.setStatus} tabs={tabs} />
          </div>
        )}
        <div className="p-5">
          <SearchInput label={`Search ${title.toLowerCase()}`} placeholder="Search sender, city or ID" value={list.q} onChange={list.setQ} />
        </div>
      </div>
      <div className="mt-4">
        {query.isLoading && <LoadingState />}
        {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
        {query.data && (query.data.items.length === 0
          ? <div className="card"><EmptyState title={emptyTitle} /></div>
          : (
            <TableCard footer={<Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={list.setPage} />}>
              <table className="min-w-full divide-y divide-ink-300/60">
                <thead><tr>
                  {columns.map(c => <th key={c.header} scope="col" className={th}>{c.header || <span className="sr-only">Actions</span>}</th>)}
                </tr></thead>
                <tbody className="divide-y divide-ink-300/40">
                  {query.data.items.map(r => (
                    <tr key={r.id}>
                      {columns.map(c => <td key={c.header} className={c.className ?? td}>{c.cell(r)}</td>)}
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableCard>
          ))}
      </div>
    </>
  );
}

const REQUEST_STATUSES = ['REQUESTED', 'MATCHED', 'PAID_ESCROW', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'DISPUTED', 'DRAFT'];

export function AdminShipmentRequests() {
  const qc = useQueryClient();
  const { list, query } = useRequestList('all', 'AWAITING_REVIEW');
  const decide = useMutation({
    mutationFn: async ({ r, approve }: { r: AdminRequestRow; approve: boolean }) => {
      if (approve) return MarketplaceApi.setApproval(r.id, true);
      const reason = window.prompt('Reason for rejecting this request (shared in the timeline):')?.trim();
      if (!reason) return null;
      return MarketplaceApi.transition(r.id, 'CANCELLED', reason);
    },
    onSuccess: (result, { approve }) => {
      if (!result) return;
      toast.success(approve ? 'Request approved and visible to travelers' : 'Request rejected');
      qc.invalidateQueries({ queryKey: ['admin-console'] });
    },
    onError: error => toast.error(errorMessage(error)),
  });
  const counts = query.data?.counts ?? {};

  return (
    <><ShipmentRequestInbox />
    <RequestListFrame title="Shipment Requests" subtitle="Approve sender requests before travelers can see them."
      list={list} query={query} emptyTitle="No requests in this view"
      tabs={statusTabs(counts, REQUEST_STATUSES, [{ value: 'AWAITING_REVIEW', label: 'Awaiting review', count: counts.AWAITING_REVIEW ?? 0 }])}
      columns={[
        { header: 'Sender', cell: r => <SenderCell r={r} /> },
        { header: 'Route', cell: r => <RouteCell r={r} /> },
        { header: 'Offered', cell: r => money(r.offeredPrice) },
        { header: 'Deadline', cell: r => shortDate(r.deadline) },
        { header: 'Offers', cell: r => r.offers },
        { header: 'Status', cell: r => (
          <div className="flex flex-col items-start gap-1">
            <AdminBadge status={r.status} />
            {r.status === 'REQUESTED' && !r.adminApproved && <AdminBadge status="AWAITING_REVIEW" />}
          </div>
        ) },
        { header: 'Updated', cell: r => relativeTime(r.updatedAt) },
        { header: '', className: `${td} text-right whitespace-nowrap`, cell: r => (
          <div className="inline-flex gap-2">
            {r.status === 'REQUESTED' && !r.adminApproved && (
              <>
                <button type="button" className={purpleButton} disabled={decide.isPending}
                  aria-label={`Approve request from ${r.senderName ?? 'sender'}`} onClick={() => decide.mutate({ r, approve: true })}>Approve</button>
                <button type="button" className="btn btn-ghost text-rose-700" disabled={decide.isPending}
                  aria-label={`Reject request from ${r.senderName ?? 'sender'}`} onClick={() => decide.mutate({ r, approve: false })}>Reject</button>
              </>
            )}
            <DetailsLink r={r} />
          </div>
        ) },
      ]} /></>
  );
}
