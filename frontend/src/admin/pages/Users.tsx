import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { AdminApi } from '../../api/endpoints';
import { MarketplaceApi } from '../../api/marketplace';
import { api } from '../../api/client';
import { AdminConsoleApi } from '../api';
import type { AdminUserRow } from '../types';
import {
  AdminBadge, Avatar, EmptyState, ErrorState, FilterSelect, LoadingState, PageHeader, Pagination, SearchInput,
  TableCard, Tabs, errorMessage, purpleOutline, shortDate, td, th, useListState,
} from '../ui';

export type UserType = 'ALL' | 'TRAVELER' | 'SENDER' | 'AGENT';
const tabRoutes: Record<UserType, string> = {
  ALL: '/admin/users', TRAVELER: '/admin/travelers', SENDER: '/admin/senders', AGENT: '/admin/agents',
};
const titles: Record<UserType, string> = { ALL: 'Users', TRAVELER: 'Travelers', SENDER: 'Senders', AGENT: 'Agents' };

export function AdminUsers({ type }: { type: UserType }) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const list = useListState();
  const users = useQuery({
    queryKey: ['admin-console', 'users', type, list.query],
    queryFn: () => AdminConsoleApi.users({ type, ...list.query }),
    placeholderData: previous => previous,
  });
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin-console'] });
  const action = useMutation({
    mutationFn: async ({ user, kind }: { user: AdminUserRow; kind: 'suspend' | 'reactivate' | 'verify' | 'activate-agent' }) => {
      if (kind === 'suspend' || kind === 'reactivate') return AdminApi.updateAccess(user.id, kind === 'reactivate');
      if (kind === 'verify') return MarketplaceApi.verify(user.id, 'ID_VERIFIED');
      return api.post(`/api/admin/agents/${user.id}/promote`, {});
    },
    onSuccess: (_, { user, kind }) => {
      toast.success({ suspend: `${user.name} suspended`, reactivate: `${user.name} reactivated`,
        verify: `${user.name} verified`, 'activate-agent': `${user.name} activated as agent` }[kind]);
      refresh();
    },
    onError: error => toast.error(errorMessage(error)),
  });
  const counts = users.data?.counts ?? {};

  return (
    <>
      <PageHeader title={titles[type]} subtitle="Search accounts, review verification and manage access." />
      <div className="card p-0">
        <div className="px-5 pt-3">
          <Tabs label="User type" value={type} onChange={value => navigate(tabRoutes[value as UserType])}
            tabs={[
              { value: 'ALL', label: 'All', count: counts.ALL },
              { value: 'TRAVELER', label: 'Travelers', count: counts.TRAVELER },
              { value: 'SENDER', label: 'Senders', count: counts.SENDER },
              { value: 'AGENT', label: 'Agents', count: counts.AGENT },
            ]} />
        </div>
        <div className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center">
          <SearchInput label="Search users" placeholder="Search name, email or phone" value={list.q} onChange={list.setQ} />
          <FilterSelect label="Status" value={list.status} onChange={list.setStatus} options={[
            { value: '', label: 'All statuses' }, { value: 'VERIFIED', label: 'Verified' },
            { value: 'PENDING', label: 'Pending' }, { value: 'SUSPENDED', label: 'Suspended' },
          ]} />
        </div>
      </div>
      <div className="mt-4">
        {users.isLoading && <LoadingState label="Loading users…" />}
        {users.isError && <ErrorState error={users.error} onRetry={() => users.refetch()} />}
        {users.data && (users.data.items.length === 0
          ? <div className="card"><EmptyState title="No users found">Adjust the search or status filter.</EmptyState></div>
          : (
            <TableCard footer={<Pagination page={users.data.page} totalPages={users.data.totalPages} onChange={list.setPage} />}>
              <table className="min-w-full divide-y divide-ink-300/60">
                <thead><tr>
                  <th scope="col" className={th}>User</th><th scope="col" className={th}>Role</th>
                  <th scope="col" className={th}>Status</th><th scope="col" className={th}>Shipments</th>
                  <th scope="col" className={th}>Trips</th><th scope="col" className={th}>Joined</th>
                  <th scope="col" className={th}><span className="sr-only">Actions</span></th>
                </tr></thead>
                <tbody className="divide-y divide-ink-300/40">
                  {users.data.items.map(user => (
                    <tr key={user.id}>
                      <td className={td}>
                        <div className="flex items-center gap-3">
                          <Avatar name={user.name} size="sm" />
                          <div className="min-w-0">
                            <div className="font-medium text-ink-900">{user.name}</div>
                            <div className="text-xs text-ink-500">{user.email}{user.phone ? ` · ${user.phone}` : ''}</div>
                          </div>
                        </div>
                      </td>
                      <td className={td}><AdminBadge status={user.role} /></td>
                      <td className={td}><AdminBadge status={user.status} /></td>
                      <td className={td}>{user.shipments}</td>
                      <td className={td}>{user.trips}</td>
                      <td className={td}>{shortDate(user.createdAt)}</td>
                      <td className={`${td} text-right whitespace-nowrap`}>
                        <UserActions user={user} busy={action.isPending}
                          onAction={kind => {
                            if (kind === 'suspend' && !window.confirm(`Suspend ${user.name}? They will be signed out.`)) return;
                            action.mutate({ user, kind });
                          }} />
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

function UserActions({ user, busy, onAction }: {
  user: AdminUserRow; busy: boolean; onAction: (kind: 'suspend' | 'reactivate' | 'verify' | 'activate-agent') => void;
}) {
  if (user.role === 'ADMIN') return null;
  const label = (verb: string) => `${verb} ${user.name}`;
  return (
    <div className="inline-flex gap-2">
      {user.active && user.status === 'PENDING' && user.role === 'TRAVELER' && (
        <button type="button" className={purpleOutline} disabled={busy} aria-label={label('Verify')} onClick={() => onAction('verify')}>Verify</button>
      )}
      {user.active && user.status === 'PENDING' && user.role === 'AGENT' && (
        <button type="button" className={purpleOutline} disabled={busy} aria-label={label('Activate agent')} onClick={() => onAction('activate-agent')}>Activate</button>
      )}
      {user.active
        ? <button type="button" className="btn btn-ghost text-rose-700" disabled={busy} aria-label={label('Suspend')} onClick={() => onAction('suspend')}>Suspend</button>
        : <button type="button" className="btn btn-ghost" disabled={busy} aria-label={label('Reactivate')} onClick={() => onAction('reactivate')}>Reactivate</button>}
    </div>
  );
}
