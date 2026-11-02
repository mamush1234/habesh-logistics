import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { PaymentsApi } from '../../api/payments';
import { ArrowDownLeft, Lock, Percent, Undo2 } from 'lucide-react';
import { AdminConsoleApi } from '../api';
import { useDateRange } from '../dateRange';
import {
  AdminBadge, EmptyState, ErrorState, KpiCard, LoadingState, PageHeader, Pagination, SearchInput, TableCard, Tabs,
  money, shortDate, td, th, useListState,
} from '../ui';

export function AdminPayments() {
  const { range } = useDateRange();
  const list = useListState();
  const payments = useQuery({
    queryKey: ['admin-console', 'payments', range, list.query],
    queryFn: () => AdminConsoleApi.payments(range, list.query),
    placeholderData: previous => previous,
  });
  const data = payments.data;
  const counts = data?.page.counts ?? {};

  return (
    <>
      <PageHeader title="Payments" subtitle={`Demo escrow funded between ${shortDate(range.from)} and ${shortDate(range.to)}.`} />
      <StripeTestPayments />
      {payments.isLoading && <LoadingState label="Loading payments…" />}
      {payments.isError && <ErrorState error={payments.error} onRetry={() => payments.refetch()} />}
      {data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <KpiCard label="Held in escrow" value={money(data.summary.held)} tone="amber" icon={<Lock className="w-5 h-5" />} />
            <KpiCard label="Released to travelers" value={money(data.summary.released)} tone="emerald" icon={<ArrowDownLeft className="w-5 h-5" />} />
            <KpiCard label="Refunded" value={money(data.summary.refunded)} tone="sky" icon={<Undo2 className="w-5 h-5" />} />
            <KpiCard label="Platform fees" value={money(data.summary.platformFees)} tone="violet" icon={<Percent className="w-5 h-5" />} />
          </div>
          <div className="card mt-6 p-0">
            <div className="px-5 pt-3">
              <Tabs label="Escrow status" value={list.status} onChange={list.setStatus} tabs={[
                { value: '', label: 'All', count: Object.values(counts).reduce((s, n) => s + n, 0) },
                { value: 'DEMO_HELD', label: 'Held', count: counts.DEMO_HELD ?? 0 },
                { value: 'DEMO_RELEASED', label: 'Released', count: counts.DEMO_RELEASED ?? 0 },
                { value: 'DEMO_REFUNDED', label: 'Refunded', count: counts.DEMO_REFUNDED ?? 0 },
              ]} />
            </div>
            <div className="p-5">
              <SearchInput label="Search payments" placeholder="Search sender, traveler or route" value={list.q} onChange={list.setQ} />
            </div>
          </div>
          <div className="mt-4">
            {data.page.items.length === 0
              ? <div className="card"><EmptyState title="No payments in this range">Payments appear once a sender funds demo escrow.</EmptyState></div>
              : (
                <TableCard footer={<Pagination page={data.page.page} totalPages={data.page.totalPages} onChange={list.setPage} />}>
                  <table className="min-w-full divide-y divide-ink-300/60">
                    <thead><tr>
                      <th scope="col" className={th}>Funded</th><th scope="col" className={th}>Sender</th>
                      <th scope="col" className={th}>Traveler</th><th scope="col" className={th}>Route</th>
                      <th scope="col" className={`${th} text-right`}>Amount</th><th scope="col" className={`${th} text-right`}>Platform fee</th>
                      <th scope="col" className={`${th} text-right`}>Traveler payout</th><th scope="col" className={th}>Escrow</th>
                    </tr></thead>
                    <tbody className="divide-y divide-ink-300/40">
                      {data.page.items.map(p => (
                        <tr key={p.requestId}>
                          <td className={td}>{shortDate(p.fundedAt)}</td>
                          <td className={td}>{p.senderName ?? '—'}</td>
                          <td className={td}>{p.travelerName ?? '—'}</td>
                          <td className={td}>{p.route}</td>
                          <td className={`${td} text-right font-medium`}>{money(p.amount)}</td>
                          <td className={`${td} text-right`}>{money(p.platformFee)}</td>
                          <td className={`${td} text-right`}>{money(p.travelerPayout)}</td>
                          <td className={td}><AdminBadge status={p.escrowStatus} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </TableCard>
              )}
          </div>
          <p className="mt-4 text-xs text-ink-500">All amounts are demo escrow; no real money moves. Fees use the current platform settings.</p>
        </>
      )}
    </>
  );
}

function StripeTestPayments() {
  const [page, setPage] = useState(0);
  const query = useQuery({ queryKey: ['admin-console', 'stripe-test-payments', page],
    queryFn: () => PaymentsApi.admin(page), refetchInterval: 10000 });
  return <section className="card mb-6 space-y-4">
    <h2 className="text-xl font-bold">Stripe test payments</h2>
    <p className="text-sm">All dates, newest first. Separate from demo escrow and revenue totals below.
      No real funds, transfers, or payouts. Amounts include the sender's accepted fee share.</p>
    {query.isPending && <LoadingState label="Loading Stripe test payments..." />}
    {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
    {query.data && (query.data.items.length === 0 ? <EmptyState title="No Stripe test payment attempts yet" /> : <>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-ink-300/60">
          <thead><tr>{['Created', 'Package', 'Test amount', 'Status'].map(label => <th key={label} scope="col" className={th}>{label}</th>)}</tr></thead>
          <tbody>{query.data.items.map(p => <tr key={p.id}>
            <td className={td}>{shortDate(p.createdAt)}</td>
            <td className={td}><Link className="underline" to={`/marketplace/requests/${p.packageId}`}>View package</Link></td>
            <td className={td}>{money(p.amountCents / 100)} {p.currency}</td>
            <td className={td}><AdminBadge status={p.status} /></td>
          </tr>)}</tbody>
        </table>
      </div>
      <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={setPage} />
    </>)}
  </section>;
}
