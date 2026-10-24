import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { toast } from 'sonner';
import { PackageQueueApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';

export function PackageNotifications() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const qc = useQueryClient();
  const query = useQuery({
    queryKey: ['package-notifications', user?.id],
    queryFn: PackageQueueApi.notifications,
    enabled: !!user && !user.mustChangePassword,
    refetchInterval: 15_000,
  });
  const read = useMutation({
    mutationFn: PackageQueueApi.markRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['package-notifications', user?.id] }),
    onError: () => toast.error('Could not mark notification as read'),
  });
  if (!user || user.mustChangePassword) return null;
  const unread = query.data?.filter(n => !n.readAt).length ?? 0;

  return <div className="relative">
    <button type="button" className="btn btn-ghost !px-2" aria-label={`Package notifications, ${unread} unread`}
      aria-expanded={open} onClick={() => setOpen(!open)}>
      <Bell className="h-5 w-5" />
      {unread > 0 && <span className="rounded-full bg-red-600 text-white px-1.5 text-xs">{unread}</span>}
      {query.isError && <span className="text-red-700 text-xs">!</span>}
    </button>
    {open && <section aria-label="Package notifications" className="fixed left-4 right-4 top-16 sm:absolute sm:left-auto sm:right-0 sm:top-auto sm:mt-2 sm:w-80 max-h-96 overflow-y-auto bg-white border rounded-xl shadow-xl p-3 space-y-3">
      <h2 className="font-semibold">Package notifications</h2>
      {query.isPending && <p role="status">Loading notifications...</p>}
      {query.isError && <p role="alert">Could not load notifications. <button className="underline" onClick={() => query.refetch()}>Retry</button></p>}
      {query.data?.length === 0 && <p className="text-sm">No notifications yet.</p>}
      {query.data?.map(n => <article key={n.id} className={`border-t pt-2 text-sm ${!n.readAt ? 'font-semibold' : ''}`}>
        <Link to={n.requestId ? '/marketplace' : user.role === 'ADMIN' ? '/legacy-packages' : '/legacy-shipments'} onClick={() => {
          if (!n.readAt) read.mutate(n.id);
          setOpen(false);
        }}>{n.message}</Link>
        <p className="text-xs text-ink-500">{new Date(n.createdAt).toLocaleString()}</p>
      </article>)}
      <Link className="text-sm underline block" to="/marketplace" onClick={() => setOpen(false)}>Open delivery marketplace</Link>
    </section>}
  </div>;
}
