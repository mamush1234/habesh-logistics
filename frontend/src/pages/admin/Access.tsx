import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { AdminApi } from '../../api/endpoints';

function report(error: unknown) {
  const message: unknown = isAxiosError(error) ? error.response?.data?.message : undefined;
  toast.error(typeof message === 'string' ? message : 'Could not update private access');
}

export function AdminAccess() {
  const qc = useQueryClient();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<'SENDER' | 'TRAVELER' | 'AGENT'>('SENDER');
  const [link, setLink] = useState('');
  const [delivery, setDelivery] = useState<{ sent: boolean; message: string } | null>(null);
  const invitations = useQuery({ queryKey: ['invitations'], queryFn: AdminApi.invitations });
  const users = useQuery({ queryKey: ['admin-users'], queryFn: () => AdminApi.users() });
  const invite = useMutation({
    mutationFn: () => AdminApi.invite(email, role),
    onSuccess: async response => {
      setLink(response.invitationUrl);
      setDelivery({ sent: response.emailSent, message: response.deliveryMessage });
      setEmail('');
      await qc.invalidateQueries({ queryKey: ['invitations'] });
      if (response.emailSent) toast.success(response.deliveryMessage);
      else toast.warning(response.deliveryMessage);
    },
    onError: report,
  });
  const revoke = useMutation({
    mutationFn: AdminApi.revokeInvitation,
    onSuccess: async () => {
      setLink('');
      setDelivery(null);
      await qc.invalidateQueries({ queryKey: ['invitations'] });
      toast.success('Invitation revoked');
    },
    onError: report,
  });
  const access = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => AdminApi.updateAccess(id, active),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin-users'] });
      toast.success('Account access updated');
    },
    onError: report,
  });

  return <div className="space-y-6">
    <h1 className="text-3xl font-bold">Private access</h1>
    <form className="card space-y-3" onSubmit={e => {
      e.preventDefault();
      setLink('');
      setDelivery(null);
      invite.mutate();
    }}>
      <h2 className="text-xl font-semibold">Invite a person</h2>
      <p className="text-sm text-ink-500">Single-use, email-bound links expire in 48 hours. Email is sent when Gmail SMTP is configured.</p>
      <label className="label" htmlFor="invite-email">Email</label>
      <input id="invite-email" className="input" type="email" required maxLength={255} value={email}
        onChange={e => setEmail(e.target.value)} data-testid="invite-email" />
      <label className="label" htmlFor="invite-role">Role</label>
      <select id="invite-role" className="input" value={role} data-testid="invite-role"
        onChange={e => {
          const selected = e.target.value;
          if (selected === 'SENDER' || selected === 'TRAVELER' || selected === 'AGENT') setRole(selected);
        }}>
        <option value="SENDER">Sender</option><option value="TRAVELER">Traveler</option><option value="AGENT">Agent</option>
      </select>
      <button className="btn btn-brand" disabled={invite.isPending} data-testid="create-invitation">
        {invite.isPending ? 'Creating and sending...' : 'Create invitation'}
      </button>
      {delivery && <p role={delivery.sent ? 'status' : 'alert'}
        className={delivery.sent ? 'text-sm text-emerald-700' : 'text-sm text-amber-700'}
        data-testid="invitation-delivery">{delivery.message}</p>}
      {link && <div className="space-y-2">
        <label className="label" htmlFor="invite-link">Save and share this link privately. It is shown only now.</label>
        <input id="invite-link" className="input" readOnly value={link} data-testid="invitation-link" onFocus={e => e.target.select()} />
        <button type="button" className="btn btn-ghost" onClick={async () => {
          try { await navigator.clipboard.writeText(link); toast.success('Invitation link copied'); }
          catch { toast.error('Could not copy. Select and copy the link manually.'); }
        }}>Copy link</button>
      </div>}
    </form>
    <section className="card space-y-3">
      <h2 className="text-xl font-semibold">Recent invitations</h2>
      {invitations.isPending && <p role="status">Loading invitations...</p>}
      {invitations.isError && <p role="alert">Could not load invitations. <button onClick={() => invitations.refetch()}>Retry</button></p>}
      {invitations.data?.map(invitation => {
        const state = invitation.revokedAt ? 'Revoked' : invitation.usedAt ? 'Used'
          : Date.parse(invitation.expiresAt) <= Date.now() ? 'Expired' : 'Pending';
        return <div key={invitation.id} className="flex flex-wrap justify-between gap-3 border-b border-ink-300 py-2">
          <span>{invitation.email} - {invitation.role} - {state}</span>
          {state === 'Pending' && <button className="btn btn-ghost text-red-600" disabled={revoke.isPending}
            onClick={() => revoke.mutate(invitation.id)}>Revoke invitation</button>}
        </div>;
      })}
    </section>
    <section className="card space-y-3">
      <h2 className="text-xl font-semibold">Approved accounts</h2>
      <p className="text-sm text-ink-500">Disabling an account blocks login and its existing tokens on the next API request.</p>
      {users.isPending && <p role="status">Loading accounts...</p>}
      {users.isError && <p role="alert">Could not load accounts. <button onClick={() => users.refetch()}>Retry</button></p>}
      {users.data?.map(user => <div key={user.id} className="flex flex-wrap justify-between gap-3 border-b border-ink-300 py-2">
        <span>{user.firstName} {user.lastName} - {user.email} - {user.role} - {user.active ? 'Active' : 'Disabled'}</span>
        {user.role !== 'ADMIN' && <button className="btn btn-ghost" disabled={access.isPending}
          onClick={() => access.mutate({ id: user.id, active: !user.active })}>{user.active ? 'Disable access' : 'Enable access'}</button>}
      </div>)}
    </section>
  </div>;
}
