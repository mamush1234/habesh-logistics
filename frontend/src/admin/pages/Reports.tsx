import { useQuery } from '@tanstack/react-query';
import { AdminConsoleApi } from '../api';
import { useDateRange } from '../dateRange';
import { EmptyState, ErrorState, LoadingState, PageHeader, money, shortDate, titleCase } from '../ui';

function Bars({ data, label }: { data: Record<string, number>; label: string }) {
  const entries = Object.entries(data);
  const max = Math.max(1, ...entries.map(([, n]) => n));
  return (
    <ul className="space-y-2" aria-label={label}>
      {entries.map(([key, n]) => (
        <li key={key} className="grid grid-cols-[8rem_1fr_2.5rem] items-center gap-3 text-sm">
          <span className="text-ink-700">{titleCase(key)}</span>
          <span className="h-2 rounded-full bg-ink-100" aria-hidden="true">
            <span className="block h-2 rounded-full bg-violet-600" style={{ width: `${(n / max) * 100}%` }} />
          </span>
          <span className="text-right font-medium text-ink-900">{n}</span>
        </li>
      ))}
    </ul>
  );
}

export function AdminReports() {
  const { range } = useDateRange();
  const report = useQuery({ queryKey: ['admin-console', 'reports', range], queryFn: () => AdminConsoleApi.report(range) });
  const data = report.data;
  const totalRequests = data ? Object.values(data.requestsByStatus).reduce((s, n) => s + n, 0) : 0;

  return (
    <>
      <PageHeader title="Reports" subtitle={`Requests created between ${shortDate(range.from)} and ${shortDate(range.to)}.`} />
      {report.isLoading && <LoadingState label="Building report…" />}
      {report.isError && <ErrorState error={report.error} onRetry={() => report.refetch()} />}
      {data && (
        <div className="grid gap-6 lg:grid-cols-2">
          <section className="card" aria-labelledby="by-status">
            <h2 id="by-status" className="mb-4 font-semibold text-ink-900">Requests by status <span className="text-ink-500 font-normal">({totalRequests})</span></h2>
            {totalRequests === 0 ? <EmptyState title="No requests in this range" /> : <Bars data={data.requestsByStatus} label="Requests by status" />}
          </section>
          <section className="card" aria-labelledby="by-role">
            <h2 id="by-role" className="mb-4 font-semibold text-ink-900">Accounts by role <span className="text-ink-500 font-normal">(all time)</span></h2>
            <Bars data={data.usersByRole} label="Accounts by role" />
          </section>
          <section className="card" aria-labelledby="top-routes">
            <h2 id="top-routes" className="mb-4 font-semibold text-ink-900">Top routes</h2>
            {data.topRoutes.length === 0 ? <EmptyState title="No routes yet" /> : (
              <table className="min-w-full text-sm">
                <thead><tr className="text-left text-xs uppercase tracking-wide text-ink-500">
                  <th scope="col" className="pb-2">Route</th><th scope="col" className="pb-2 text-right">Requests</th>
                  <th scope="col" className="pb-2 text-right">Offered value</th>
                </tr></thead>
                <tbody className="divide-y divide-ink-300/40">
                  {data.topRoutes.map(r => (
                    <tr key={r.route}><td className="py-2">{r.route}</td><td className="py-2 text-right">{r.requests}</td>
                      <td className="py-2 text-right">{money(r.value)}</td></tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
          <section className="card" aria-labelledby="ratings">
            <h2 id="ratings" className="mb-4 font-semibold text-ink-900">Traveler ratings</h2>
            {data.ratings === 0 ? <EmptyState title="No ratings in this range" /> : (
              <p className="text-4xl font-bold text-ink-900">{Number(data.averageRating).toFixed(2)}
                <span className="ml-2 text-base font-normal text-ink-500">/ 5 from {data.ratings} rating{data.ratings === 1 ? '' : 's'}</span></p>
            )}
          </section>
        </div>
      )}
    </>
  );
}
