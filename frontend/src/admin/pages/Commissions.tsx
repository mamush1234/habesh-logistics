import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { AdminConsoleApi } from '../api';
import {
  AdminBadge, EmptyState, ErrorState, LoadingState, PageHeader, Pagination, SearchInput, TableCard, Tabs,
  errorMessage, money, purpleOutline, shortDate, td, th, useListState,
} from '../ui';

export function AdminCommissions() {
  const qc = useQueryClient();
  const list = useListState();
  const commissions = useQuery({
    queryKey: ['admin-console', 'commissions', list.query],
    queryFn: () => AdminConsoleApi.commissions(list.query),
    placeholderData: previous => previous,
  });
  const pay = useMutation({
    mutationFn: AdminConsoleApi.payCommission,
    onSuccess: () => { toast.success('Commission marked as paid'); qc.invalidateQueries({ queryKey: ['admin-console'] }); },
    onError: error => toast.error(errorMessage(error)),
  });
  const counts = commissions.data?.counts ?? {};

  return (
    <>
      <PageHeader title="Commissions" subtitle="Agent commissions created when agent-booked deliveries complete." />
      <div className="card p-0">
        <div className="px-5 pt-3">
          <Tabs label="Commission status" value={list.status} onChange={list.setStatus} tabs={[
            { value: '', label: 'All', count: Object.values(counts).reduce((s, n) => s + n, 0) },
            { value: 'PENDING', label: 'Pending', count: counts.PENDING ?? 0 },
            { value: 'PAID', label: 'Paid', count: counts.PAID ?? 0 },
            { value: 'CANCELLED', label: 'Cancelled', count: counts.CANCELLED ?? 0 },
          ]} />
        </div>
        <div className="p-5">
          <SearchInput label="Search commissions" placeholder="Search agent, customer or route" value={list.q} onChange={list.setQ} />
        </div>
      </div>
      <div className="mt-4">
        {commissions.isLoading && <LoadingState label="Loading commissions…" />}
        {commissions.isError && <ErrorState error={commissions.error} onRetry={() => commissions.refetch()} />}
        {commissions.data && (commissions.data.items.length === 0
          ? <div className="card"><EmptyState title="No commissions yet" /></div>
          : (
            <TableCard footer={<Pagination page={commissions.data.page} totalPages={commissions.data.totalPages} onChange={list.setPage} />}>
              <table className="min-w-full divide-y divide-ink-300/60">
                <thead><tr>
                  <th scope="col" className={th}>Agent</th><th scope="col" className={th}>Customer</th>
                  <th scope="col" className={th}>Route</th><th scope="col" className={`${th} text-right`}>Delivery</th>
                  <th scope="col" className={`${th} text-right`}>Rate</th><th scope="col" className={`${th} text-right`}>Commission</th>
                  <th scope="col" className={th}>Status</th><th scope="col" className={th}>Created</th>
                  <th scope="col" className={th}><span className="sr-only">Actions</span></th>
                </tr></thead>
                <tbody className="divide-y divide-ink-300/40">
                  {commissions.data.items.map(c => (
                    <tr key={c.id}>
                      <td className={td}>{c.agentName}</td>
                      <td className={td}>{c.customerName ?? '—'}</td>
                      <td className={td}>{c.route}</td>
                      <td className={`${td} text-right`}>{money(c.baseAmount)}</td>
                      <td className={`${td} text-right`}>{(Number(c.rate) * 100).toFixed(1)}%</td>
                      <td className={`${td} text-right font-medium`}>{money(c.amount)}</td>
                      <td className={td}><AdminBadge status={c.status} /></td>
                      <td className={td}>{shortDate(c.createdAt)}{c.paidAt && <div className="text-xs text-ink-500">Paid {shortDate(c.paidAt)}</div>}</td>
                      <td className={`${td} text-right`}>
                        {c.status === 'PENDING' && (
                          <button type="button" className={purpleOutline} disabled={pay.isPending}
                            aria-label={`Mark commission for ${c.agentName} as paid`}
                            onClick={() => { if (window.confirm(`Mark ${money(c.amount)} to ${c.agentName} as paid?`)) pay.mutate(c.id); }}>
                            Mark paid
                          </button>
                        )}
                      </td>
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
