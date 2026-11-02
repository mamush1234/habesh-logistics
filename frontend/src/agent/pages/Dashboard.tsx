import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { BadgeDollarSign, Clock, Package, PackagePlus, Plane, UserPlus, Users } from 'lucide-react';
import { AgentApi } from '../api';
import type { AgentActivity } from '../types';
import {
  Avatar, EmptyState, ErrorState, greenButton, LoadingState, money, outlineButton, PageHeader, relativeTime, StatCard,
  StatusBadge,
} from '../ui';

const activityText: Record<string, string> = {
  CUSTOMER_CREATED: 'Registered new customer', AUTHORIZATION_GRANTED: 'Authorization granted',
  AUTHORIZATION_REVOKED: 'Authorization revoked', PACKAGE_CREATED: 'Package created',
  PACKAGE_DRAFT_SAVED: 'Package draft saved', PACKAGE_SUBMITTED: 'Draft package submitted', TRIP_CREATED: 'Trip posted',
};

function ActivityRow({ activity }: { activity: AgentActivity }) {
  return (
    <li className="flex items-start gap-3 py-3">
      <Avatar name={activity.customerName ?? 'Agent'} size="sm" />
      <div className="min-w-0 flex-1">
        <p className="text-sm text-ink-900">
          <span className="font-semibold">{activityText[activity.action] ?? activity.action}</span>
          {activity.customerName && <> for {activity.customerName}</>}
        </p>
        {activity.details && <p className="text-xs text-ink-500 truncate">{activity.details}</p>}
      </div>
      <time className="text-xs text-ink-500 whitespace-nowrap" dateTime={activity.createdAt}>{relativeTime(activity.createdAt)}</time>
    </li>
  );
}

export function AgentDashboardPage() {
  const query = useQuery({ queryKey: ['agent', 'dashboard'], queryFn: AgentApi.dashboard });
  if (query.isPending) return <LoadingState label="Loading dashboard…" />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  const d = query.data;

  return (
    <>
      <PageHeader title={`Welcome back, ${d.agentName.split(' ')[0]}`}
        subtitle={`Commission rate ${(Number(d.commissionRate) * 100).toFixed(1)}%${d.referralCode ? ` · Referral code ${d.referralCode}` : ''}`}
        actions={<>
          <Link to="/agent/customers/new" className={outlineButton}><UserPlus className="w-4 h-4" aria-hidden="true" /> Register Customer</Link>
          <Link to="/agent/packages/new" className={greenButton}><PackagePlus className="w-4 h-4" aria-hidden="true" /> Create Package</Link>
        </>} />

      <section aria-label="Summary" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4 mb-6">
        <StatCard label="Active Customers" value={d.activeCustomers} icon={<Users className="w-6 h-6" />} />
        <StatCard label="Active Shipments" value={d.activeShipments} icon={<Package className="w-6 h-6" />} tone="sky" />
        <StatCard label="Pending Requests" value={d.pendingRequests} icon={<Clock className="w-6 h-6" />} tone="amber" />
        <StatCard label="Total Commissions" value={money(d.totalCommissions)} icon={<BadgeDollarSign className="w-6 h-6" />} tone="violet" />
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="card" aria-labelledby="recent-activity">
          <div className="flex items-center justify-between mb-2">
            <h2 id="recent-activity" className="text-lg font-semibold">Recent Activity</h2>
            <Link to="/agent/requests" className="text-sm text-emerald-700 hover:underline">View requests</Link>
          </div>
          {d.recentActivity.length === 0
            ? <EmptyState title="No activity yet">Register a customer to get started.</EmptyState>
            : <ul className="divide-y divide-ink-100">{d.recentActivity.map(a => <ActivityRow key={a.id} activity={a} />)}</ul>}
        </section>

        <section className="card" aria-labelledby="my-customers">
          <div className="flex items-center justify-between mb-2">
            <h2 id="my-customers" className="text-lg font-semibold">My Customers</h2>
            <Link to="/agent/customers" className="text-sm text-emerald-700 hover:underline">View all</Link>
          </div>
          {d.recentCustomers.length === 0
            ? <EmptyState title="No customers yet" action={<Link to="/agent/customers/new" className={greenButton}>Add Customer</Link>} />
            : (
              <ul className="divide-y divide-ink-100">
                {d.recentCustomers.map(c => (
                  <li key={c.id} className="flex items-center gap-3 py-3">
                    <Avatar name={c.name} />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold text-ink-900 truncate">{c.name}</p>
                      <p className="text-xs text-ink-500 truncate">{c.phone ?? c.email}</p>
                    </div>
                    <StatusBadge status={c.type} />
                    <Link to={`/agent/customers/${c.id}`} className="btn btn-ghost !py-1 !px-3 text-xs" aria-label={`View ${c.name}`}>View</Link>
                  </li>
                ))}
              </ul>
            )}
        </section>
      </div>

      <section className="grid gap-4 sm:grid-cols-2 mt-6" aria-label="Quick actions">
        <Link to="/agent/trips/new" className="card flex items-center gap-3 hover:ring-emerald-300 focus-visible:ring-2 focus-visible:ring-emerald-600 outline-none">
          <Plane className="w-6 h-6 text-emerald-700" aria-hidden="true" />
          <span><span className="block font-semibold">Post a trip</span><span className="text-sm text-ink-500">For one of your traveler customers</span></span>
        </Link>
        <Link to="/agent/commissions" className="card flex items-center gap-3 hover:ring-emerald-300 focus-visible:ring-2 focus-visible:ring-emerald-600 outline-none">
          <BadgeDollarSign className="w-6 h-6 text-emerald-700" aria-hidden="true" />
          <span><span className="block font-semibold">My commissions</span><span className="text-sm text-ink-500">Track pending and paid earnings</span></span>
        </Link>
      </section>
    </>
  );
}
