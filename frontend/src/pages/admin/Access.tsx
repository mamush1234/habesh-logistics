import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { AdminApi } from '../../api/endpoints';

export function AdminAccess() {
  const qc = useQueryClient();
  const users = useQuery({ queryKey: ['admin-users'], queryFn: () => AdminApi.users() });
  const access = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => AdminApi.updateAccess(id, active),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin-users'] });
      toast.success('Account access updated');
    },
    onError: (error: unknown) => toast.error(isAxiosError<{ message?: string }>(error)
      ? error.response?.data?.message || 'Could not update account access' : 'Could not update account access'),
  });
  return <div className="space-y-6">
    <h1 className="text-3xl font-bold">Account access</h1>
    <p>Senders and travelers can sign up directly. Create agent accounts from the <Link to="/admin" className="underline">admin dashboard</Link>.</p>
    <section className="card space-y-3">
      <h2 className="text-xl font-semibold">Accounts</h2>
      <p className="text-sm text-ink-500">Disabling an account blocks login and revokes existing sessions.</p>
      {users.isPending && <p role="status">Loading accounts...</p>}
      {users.isError && <p role="alert">Could not load accounts. <button className="underline" onClick={() => users.refetch()}>Retry</button></p>}
      {users.data?.map(user => <div key={user.id} className="flex flex-wrap justify-between gap-3 border-b border-ink-300 py-2">
        <span>{user.firstName} {user.lastName} - {user.email} - {user.role} - {user.active ? 'Active' : 'Disabled'}</span>
        {user.role !== 'ADMIN' && <button className="btn btn-ghost" disabled={access.isPending}
          onClick={() => access.mutate({ id: user.id, active: !user.active })}>{user.active ? 'Disable access' : 'Enable access'}</button>}
      </div>)}
    </section>
  </div>;
}
