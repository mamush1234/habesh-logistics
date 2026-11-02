import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { KeyRound, MessageSquare } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { AgentApi } from '../api';
import { Avatar, EmptyState, ErrorState, greenButton, LoadingState, outlineButton, PageHeader, StatusBadge } from '../ui';

export function MessagesPage() {
  const query = useQuery({ queryKey: ['agent', 'packages', 'messages'], queryFn: () => AgentApi.packages(undefined, 0) });
  const conversations = query.data?.items.filter(p => p.status !== 'DRAFT') ?? [];
  return (
    <>
      <PageHeader title="Messages" subtitle="Package chats open once a request is submitted. Open a package to read or send messages." />
      <section className="card">
        {query.isPending ? <LoadingState label="Loading conversations…" />
          : query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} />
          : conversations.length === 0 ? <EmptyState title="No conversations yet">Submit a package to start a conversation.</EmptyState>
          : (
            <ul className="divide-y divide-ink-100">
              {conversations.map(p => (
                <li key={p.id} className="flex flex-wrap items-center gap-3 py-3">
                  <Avatar name={p.customerName} />
                  <div className="min-w-0 flex-1">
                    <div className="font-semibold">{p.pickupCity} → {p.destinationCity}</div>
                    <div className="text-xs text-ink-500">{p.customerName}</div>
                  </div>
                  <StatusBadge status={p.status} />
                  <Link to={`/marketplace/requests/${p.id}`} className={outlineButton} aria-label={`Open chat for ${p.pickupCity} to ${p.destinationCity}`}>
                    <MessageSquare className="w-4 h-4" aria-hidden="true" /> Open chat
                  </Link>
                </li>
              ))}
            </ul>
          )}
      </section>
    </>
  );
}

export function SettingsPage() {
  const { user } = useAuth();
  const query = useQuery({ queryKey: ['agent', 'dashboard'], queryFn: AgentApi.dashboard });
  const name = user ? `${user.firstName} ${user.lastName}` : '';
  return (
    <>
      <PageHeader title="Settings" />
      <section className="card max-w-2xl" aria-labelledby="profile-heading">
        <h2 id="profile-heading" className="text-lg font-semibold mb-4">Agent profile</h2>
        <div className="flex items-center gap-4 mb-4">
          <Avatar name={name || 'Agent'} size="lg" />
          <div>
            <div className="font-semibold">{name}</div>
            <div className="text-sm text-ink-500">{user?.email}</div>
            {user?.phone && <div className="text-sm text-ink-500">{user.phone}</div>}
          </div>
        </div>
        {query.isPending ? <LoadingState label="Loading profile…" />
          : query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} />
          : (
            <dl className="grid gap-3 sm:grid-cols-2 text-sm">
              <div className="rounded-xl bg-ink-100 p-3"><dt className="text-ink-500">Commission rate</dt><dd className="font-semibold">{(Number(query.data.commissionRate) * 100).toFixed(1)}%</dd></div>
              <div className="rounded-xl bg-ink-100 p-3"><dt className="text-ink-500">Referral code</dt><dd className="font-semibold">{query.data.referralCode ?? '—'}</dd></div>
            </dl>
          )}
        <div className="flex flex-wrap gap-2 mt-6">
          <Link to="/account" className={greenButton}><KeyRound className="w-4 h-4" aria-hidden="true" /> Change password</Link>
          <Link to="/marketplace" className="btn btn-ghost">Open marketplace</Link>
        </div>
      </section>
    </>
  );
}
