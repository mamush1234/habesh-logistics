import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { GuestRequestsApi } from '../../api/guestRequests';
import { marketError } from '../Marketplace';

export function AdminGuestLinks() {
  const qc = useQueryClient();
  const [label, setLabel] = useState('');
  const [shareUrl, setShareUrl] = useState('');
  const links = useQuery({ queryKey: ['admin-guest-links'], queryFn: GuestRequestsApi.list, refetchInterval: 15_000 });
  const create = useMutation({
    mutationFn: () => GuestRequestsApi.create(label),
    onSuccess: data => {
      const url = new URL('/guest-request', window.location.origin);
      url.hash = new URLSearchParams({ token: data.token }).toString();
      setShareUrl(url.toString()); setLabel('');
      qc.invalidateQueries({ queryKey: ['admin-guest-links'] });
      toast.success('Private guest link created. Copy it before leaving this page.');
    },
    onError: marketError,
  });
  const revoke = useMutation({ mutationFn: GuestRequestsApi.revoke,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-guest-links'] }); toast.success('Guest link revoked'); },
    onError: marketError });
  return <div className="max-w-4xl mx-auto space-y-5">
    <Link className="underline" to="/admin">Back to admin dashboard</Link>
    <h1 className="text-3xl font-bold">Guest sender links</h1>
    <p>Create a private link for one sender. Each link expires in seven days and accepts one package request. Email and phone are required. New emails get a sender account with a unique temporary password shown once to the customer; existing accounts are unchanged. You will handle offers and contact the sender directly.</p>
    <form className="card space-y-3" onSubmit={e => { e.preventDefault(); create.mutate(); }}>
      <label className="label">Private admin label<input className="input" required maxLength={120} value={label} onChange={e => setLabel(e.target.value)} placeholder="Sender name or reference" /></label>
      <button className="btn btn-brand" disabled={create.isPending || !label.trim()}>{create.isPending ? 'Creating...' : 'Create guest link'}</button>
    </form>
    {shareUrl && <section className="card space-y-3">
      <h2 className="text-xl font-bold">Copy and share this private link</h2>
      <p>The full link is shown only now. Keep it private and send it to the intended sender. If lost, revoke the unused link and create another.</p>
      <label className="label">Guest request link<input className="input" readOnly value={shareUrl} onFocus={e => e.currentTarget.select()} /></label>
      <button className="btn btn-primary" onClick={async () => {
        try { await navigator.clipboard.writeText(shareUrl); toast.success('Link copied'); }
        catch { toast.error('Could not copy automatically. Select the link above and copy it manually.'); }
      }}>Copy link</button>
      {window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
        ? <p role="note" className="text-amber-800">This is a local development link. To share with someone on another device, create the link on your deployed HTTPS website.</p> : null}
    </section>}
    <section className="space-y-3">
      <h2 className="text-xl font-bold">Created links</h2>
      {links.isPending && <p role="status">Loading guest links...</p>}
      {links.isError && <p role="alert">Could not load guest links. <button className="underline" onClick={() => links.refetch()}>Retry</button></p>}
      {links.data?.length === 0 && <p>No guest links yet.</p>}
      {links.data?.map(link => <article className="card space-y-2" key={link.id}>
        <h3 className="font-bold break-words">{link.label}</h3>
        <p>{link.status} &middot; Expires {new Date(link.expiresAt).toLocaleString()}</p>
        {link.requestId && <Link className="btn btn-primary" to={`/marketplace/requests/${link.requestId}`}>View request, contact details and offers</Link>}
        {link.status === 'OPEN' && <button className="btn btn-ghost" disabled={revoke.isPending}
          onClick={() => revoke.mutate(link.id)}>Revoke link</button>}
      </article>)}
    </section>
  </div>;
}
