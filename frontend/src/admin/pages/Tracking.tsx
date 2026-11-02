import { useMutation, useQueryClient } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { toast } from 'sonner';
import { MarketplaceApi, type DeliveryStatus } from '../../api/marketplace';
import type { AdminRequestRow } from '../types';
import { AdminBadge, errorMessage, purpleOutline, relativeTime, td } from '../ui';
import { DetailsLink, RequestListFrame, RouteCell, SenderCell, statusTabs, useRequestList } from './Requests';

const STEPS: DeliveryStatus[] = ['PAID_ESCROW', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED', 'DELIVERED'];
const NEXT: Partial<Record<DeliveryStatus, DeliveryStatus>> = {
  PAID_ESCROW: 'PICKED_UP', PICKED_UP: 'IN_TRANSIT', IN_TRANSIT: 'ARRIVED',
};
const STEP_LABEL: Record<string, string> = { PAID_ESCROW: 'Paid', PICKED_UP: 'Picked up', IN_TRANSIT: 'In transit', ARRIVED: 'Arrived', DELIVERED: 'Delivered' };

function Progress({ status }: { status: DeliveryStatus }) {
  const current = STEPS.indexOf(status);
  return (
    <ol className="flex items-center gap-1" aria-label={`Progress: ${STEP_LABEL[status] ?? status}`}>
      {STEPS.map((step, i) => (
        <li key={step} className={clsx('h-1.5 w-8 rounded-full', i <= current ? 'bg-violet-600' : 'bg-ink-100')}
          title={STEP_LABEL[step]}>
          <span className="sr-only">{STEP_LABEL[step]}{i <= current ? ' (done)' : ''}</span>
        </li>
      ))}
    </ol>
  );
}

export function AdminTracking() {
  const qc = useQueryClient();
  const { list, query } = useRequestList('tracking');
  const advance = useMutation({
    mutationFn: ({ r, to }: { r: AdminRequestRow; to: DeliveryStatus }) => MarketplaceApi.transition(r.id, to),
    onSuccess: (_, { to }) => {
      toast.success(`Marked ${STEP_LABEL[to]?.toLowerCase() ?? to}`);
      qc.invalidateQueries({ queryKey: ['admin-console'] });
      qc.invalidateQueries({ queryKey: ['marketplace'] });
    },
    onError: error => toast.error(errorMessage(error)),
  });

  return (
    <RequestListFrame title="Tracking" subtitle="Funded deliveries that are on their way. After arrival, open the package to verify its receiver code or use its legacy confirmation flow."
      list={list} query={query} emptyTitle="Nothing is in transit"
      tabs={statusTabs(query.data?.counts ?? {}, STEPS)}
      columns={[
        { header: 'Route', cell: r => <RouteCell r={r} /> },
        { header: 'Sender', cell: r => <SenderCell r={r} /> },
        { header: 'Traveler', cell: r => r.travelerName ?? '—' },
        { header: 'Progress', cell: r => (
          <div className="space-y-1"><AdminBadge status={r.status} /><Progress status={r.status} /></div>
        ) },
        { header: 'Last update', cell: r => r.lastEvent ? (
          <div className="max-w-xs">
            <div className="truncate" title={r.lastEvent}>{r.lastEvent}</div>
            {r.lastEventAt && <div className="text-xs text-ink-500">{relativeTime(r.lastEventAt)}</div>}
          </div>
        ) : '—' },
        { header: '', className: `${td} text-right whitespace-nowrap`, cell: r => {
          const next = NEXT[r.status];
          return (
            <div className="inline-flex gap-2">
              {next && (
                <button type="button" className={purpleOutline} disabled={advance.isPending}
                  aria-label={`Mark ${r.pickupCity} to ${r.destinationCity} as ${STEP_LABEL[next]}`}
                  onClick={() => { if (window.confirm(`Mark this delivery as ${STEP_LABEL[next].toLowerCase()}?`)) advance.mutate({ r, to: next }); }}>
                  Mark {STEP_LABEL[next].toLowerCase()}
                </button>
              )}
              <DetailsLink r={r} />
            </div>
          );
        } },
      ]} />
  );
}
