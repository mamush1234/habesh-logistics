import { useState, type FormEvent } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { BadgeDollarSign, CheckCircle2, Clock } from 'lucide-react';
import { AgentApi } from '../api';
import type { CommissionStatus } from '../types';
import {
  EmptyState, ErrorState, FieldError, greenButton, LoadingState, money, PageHeader, Pagination, shortDate, StatCard,
  StatusBadge,
} from '../ui';

const isoDay = (offset = 0) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return d.toLocaleDateString('en-CA');
};

export function CommissionsPage() {
  const [draft, setDraft] = useState({ from: isoDay(-90), to: isoDay() });
  const [range, setRange] = useState(draft);
  const [rangeError, setRangeError] = useState<string>();
  const [status, setStatus] = useState<CommissionStatus | ''>('');
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['agent', 'commissions', range.from, range.to, status, page],
    queryFn: () => AgentApi.commissions({ from: range.from || undefined, to: range.to || undefined, status: status || undefined, page }),
    placeholderData: keepPreviousData,
  });

  const apply = (event: FormEvent) => {
    event.preventDefault();
    if (draft.from && draft.to && draft.from > draft.to) { setRangeError('Start date must be on or before end date'); return; }
    setRangeError(undefined);
    setRange(draft);
    setPage(0);
  };

  return (
    <>
      <PageHeader title="My Commissions" subtitle="Earned when a delivery you created is completed." />
      <form className="card mb-6 flex flex-wrap items-end gap-4" onSubmit={apply} noValidate aria-label="Commission date range">
        <div>
          <label className="label" htmlFor="commission-from">From</label>
          <input id="commission-from" type="date" className="input" value={draft.from} max={draft.to || undefined}
            onChange={e => setDraft({ ...draft, from: e.target.value })} aria-describedby="commission-range-error" />
        </div>
        <div>
          <label className="label" htmlFor="commission-to">To</label>
          <input id="commission-to" type="date" className="input" value={draft.to} min={draft.from || undefined}
            onChange={e => setDraft({ ...draft, to: e.target.value })} aria-describedby="commission-range-error" />
        </div>
        <div>
          <label className="label" htmlFor="commission-status">Status</label>
          <select id="commission-status" className="input" value={status} onChange={e => { setStatus(e.target.value as CommissionStatus | ''); setPage(0); }}>
            <option value="">All</option><option value="PENDING">Pending</option><option value="PAID">Paid</option><option value="CANCELLED">Cancelled</option>
          </select>
        </div>
        <button type="submit" className={greenButton}>Apply</button>
        <div className="w-full -mt-2"><FieldError id="commission-range-error" message={rangeError} /></div>
      </form>

      {query.isPending ? <LoadingState label="Loading commissions…" />
        : query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} />
        : (
          <>
            <section className="grid gap-4 sm:grid-cols-3 mb-6" aria-label="Commission totals">
              <StatCard label="Total" value={money(query.data.total)} icon={<BadgeDollarSign className="w-6 h-6" />} hint="Pending + paid in range" />
              <StatCard label="Pending" value={money(query.data.pending)} icon={<Clock className="w-6 h-6" />} tone="amber" />
              <StatCard label="Paid" value={money(query.data.paid)} icon={<CheckCircle2 className="w-6 h-6" />} />
            </section>
            <section className="card" aria-labelledby="commission-table">
              <h2 id="commission-table" className="text-lg font-semibold mb-2">Commission history</h2>
              {query.data.items.length === 0 ? (
                <EmptyState title="No commissions in this range">Commissions appear when a delivery you created is completed.</EmptyState>
              ) : (
                <>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="text-left text-ink-500 border-b border-ink-100">
                          <th scope="col" className="py-2 pr-3 font-medium">Date</th>
                          <th scope="col" className="py-2 pr-3 font-medium">Route</th>
                          <th scope="col" className="py-2 pr-3 font-medium hidden md:table-cell">Customer</th>
                          <th scope="col" className="py-2 pr-3 font-medium hidden sm:table-cell">Delivery price</th>
                          <th scope="col" className="py-2 pr-3 font-medium hidden sm:table-cell">Rate</th>
                          <th scope="col" className="py-2 pr-3 font-medium">Commission</th>
                          <th scope="col" className="py-2 font-medium">Status</th>
                        </tr>
                      </thead>
                      <tbody className={clsx(query.isPlaceholderData && 'opacity-60')}>
                        {query.data.items.map(c => (
                          <tr key={c.id} className="border-b border-ink-100 last:border-0">
                            <td className="py-3 pr-3 whitespace-nowrap">{shortDate(c.createdAt)}</td>
                            <td className="py-3 pr-3">{c.route}</td>
                            <td className="py-3 pr-3 hidden md:table-cell">{c.customerName ?? '—'}</td>
                            <td className="py-3 pr-3 hidden sm:table-cell">{money(c.baseAmount)}</td>
                            <td className="py-3 pr-3 hidden sm:table-cell">{(Number(c.rate) * 100).toFixed(1)}%</td>
                            <td className="py-3 pr-3 font-semibold">{money(c.amount)}</td>
                            <td className="py-3"><StatusBadge status={c.status} /></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={setPage} />
                </>
              )}
            </section>
          </>
        )}
    </>
  );
}
