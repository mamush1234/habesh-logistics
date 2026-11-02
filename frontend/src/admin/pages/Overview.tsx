import { useQuery } from '@tanstack/react-query';
import { Activity as ActivityIcon, ClipboardCheck, DollarSign, Percent, Truck, UserPlus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { AdminConsoleApi } from '../api';
import { useDateRange } from '../dateRange';
import type { Activity } from '../types';
import { EmptyState, ErrorState, KpiCard, LoadingState, PageHeader, money, purpleOutline, relativeTime, shortDate } from '../ui';
import { RevenueChart } from './RevenueChart';

const activityIcon: Record<Activity['type'], JSX.Element> = {
  DELIVERY: <Truck className="w-4 h-4" />,
  AGENT: <ActivityIcon className="w-4 h-4" />,
  USER: <UserPlus className="w-4 h-4" />,
};

export function AdminOverview() {
  const { range } = useDateRange();
  const overview = useQuery({ queryKey: ['admin-console', 'overview', range], queryFn: () => AdminConsoleApi.overview(range) });
  const data = overview.data;

  return (
    <>
      <PageHeader title="Dashboard" subtitle={`Platform performance from ${shortDate(range.from)} to ${shortDate(range.to)}`}
        actions={<Link to="/admin/operations" className={purpleOutline}>Operations board</Link>} />
      {overview.isLoading && <LoadingState label="Loading overview…" />}
      {overview.isError && <ErrorState error={overview.error} onRetry={() => overview.refetch()} />}
      {data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <KpiCard label="Total Revenue" value={money(data.totalRevenue)} tone="violet" icon={<DollarSign className="w-5 h-5" />}
              hint="Funded escrow in range" />
            <KpiCard label="Platform Fees" value={money(data.platformFees)} tone="emerald" icon={<Percent className="w-5 h-5" />}
              hint={`${data.platformFeePercent}% of revenue`} />
            <KpiCard label="Active Shipments" value={data.activeShipments} tone="sky" icon={<Truck className="w-5 h-5" />}
              hint="Matched through delivered" />
            <KpiCard label="Pending Reviews" value={data.pendingReviews} tone="amber" icon={<ClipboardCheck className="w-5 h-5" />}
              hint="Requests awaiting approval" />
          </div>
          <div className="grid gap-6 xl:grid-cols-3">
            <section className="card xl:col-span-2" aria-labelledby="chart-heading">
              <div className="mb-4 flex flex-wrap items-baseline justify-between gap-2">
                <h2 id="chart-heading" className="font-semibold text-ink-900">Revenue &amp; Bookings</h2>
                <span className="text-sm text-ink-500">{data.totalBookings} bookings · per {data.bucket === 'WEEK' ? 'week' : 'day'}</span>
              </div>
              <RevenueChart points={data.chart} bucket={data.bucket} />
            </section>
            <section className="card" aria-labelledby="activity-heading">
              <h2 id="activity-heading" className="mb-4 font-semibold text-ink-900">Recent Activities</h2>
              {data.activities.length === 0
                ? <EmptyState title="No activity in this range">Try a wider date range.</EmptyState>
                : (
                  <ul className="space-y-4">
                    {data.activities.map((a, i) => (
                      <li key={`${a.at}-${i}`} className="flex gap-3">
                        <span className="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-full bg-violet-100 text-violet-700" aria-hidden="true">
                          {activityIcon[a.type]}
                        </span>
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-ink-900">{a.title}</p>
                          <p className="truncate text-xs text-ink-500" title={a.detail}>{a.detail}</p>
                          <p className="text-xs text-ink-500"><time dateTime={a.at}>{relativeTime(a.at)}</time></p>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
            </section>
          </div>
        </div>
      )}
    </>
  );
}
