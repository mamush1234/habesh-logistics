import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { AdminConsoleApi } from '../api';
import type { AdminRequestRow } from '../types';
import { AdminBadge, errorMessage, money, purpleButton, purpleOutline, relativeTime, td } from '../ui';
import { DetailsLink, RequestListFrame, RouteCell, SenderCell, statusTabs, useRequestList } from './Requests';

type Outcome = 'COMPLETED' | 'CANCELLED';

export function AdminDisputes() {
  const { list, query } = useRequestList('disputes', 'DISPUTED');
  const [resolving, setResolving] = useState<AdminRequestRow | null>(null);

  return (
    <>
      <RequestListFrame title="Disputes" subtitle="Resolve disputed deliveries by releasing escrow to the traveler or refunding the sender."
        list={list} query={query} emptyTitle="No disputes in this view"
        tabs={statusTabs(query.data?.counts ?? {}, ['DISPUTED', 'COMPLETED', 'CANCELLED'])}
        columns={[
          { header: 'Route', cell: r => <RouteCell r={r} /> },
          { header: 'Sender', cell: r => <SenderCell r={r} /> },
          { header: 'Traveler', cell: r => r.travelerName ?? '—' },
          { header: 'Escrow', cell: r => <div>{money(r.escrowAmount)}<div className="mt-1"><AdminBadge status={r.escrowStatus} /></div></div> },
          { header: 'Reason', cell: r => <p className="max-w-xs text-sm" title={r.disputeReason ?? ''}>{r.disputeReason ?? '—'}</p> },
          { header: 'Status', cell: r => <div><AdminBadge status={r.status} /><div className="text-xs text-ink-500 mt-1">{relativeTime(r.updatedAt)}</div></div> },
          { header: '', className: `${td} text-right whitespace-nowrap`, cell: r => (
            <div className="inline-flex gap-2">
              {r.status === 'DISPUTED' && (
                <button type="button" className={purpleButton} onClick={() => setResolving(r)}
                  aria-label={`Resolve dispute for ${r.pickupCity} to ${r.destinationCity}`}>Resolve</button>
              )}
              <DetailsLink r={r} />
            </div>
          ) },
        ]} />
      {resolving && <ResolveDialog request={resolving} onClose={() => setResolving(null)} />}
    </>
  );
}

function ResolveDialog({ request, onClose }: { request: AdminRequestRow; onClose: () => void }) {
  const qc = useQueryClient();
  const dialog = useRef<HTMLDialogElement>(null);
  const [outcome, setOutcome] = useState<Outcome>('COMPLETED');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const resolve = useMutation({
    mutationFn: () => AdminConsoleApi.resolveDispute(request.id, outcome, reason.trim()),
    onSuccess: () => {
      toast.success(outcome === 'COMPLETED' ? 'Escrow released to traveler' : 'Sender refunded');
      qc.invalidateQueries({ queryKey: ['admin-console'] });
      onClose();
    },
    onError: e => setError(errorMessage(e)),
  });

  useEffect(() => {
    const d = dialog.current;
    if (d && !d.open) d.showModal();
  }, []);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) { setError('Explain the decision; it is recorded in the delivery timeline.'); return; }
    setError('');
    resolve.mutate();
  };

  return (
    <dialog ref={dialog} onClose={onClose} aria-labelledby="resolve-title"
      className="w-full max-w-lg rounded-2xl p-0 shadow-xl backdrop:bg-black/40">
      <form onSubmit={submit} className="space-y-4 p-6">
        <div>
          <h2 id="resolve-title" className="text-lg font-semibold text-ink-900">Resolve dispute</h2>
          <p className="text-sm text-ink-500">{request.pickupCity} → {request.destinationCity} · {money(request.escrowAmount)} in escrow</p>
        </div>
        {request.disputeReason && <p className="rounded-xl bg-rose-50 p-3 text-sm text-rose-900">“{request.disputeReason}”</p>}
        <fieldset>
          <legend className="label">Outcome</legend>
          <div className="space-y-2">
            {([['COMPLETED', 'Release escrow to the traveler (mark completed)'], ['CANCELLED', 'Refund the sender (cancel delivery)']] as const).map(([value, text]) => (
              <label key={value} className="flex items-center gap-2 text-sm">
                <input type="radio" name="outcome" value={value} checked={outcome === value} onChange={() => setOutcome(value)} />
                {text}
              </label>
            ))}
          </div>
        </fieldset>
        <div>
          <label htmlFor="resolve-reason" className="label">Reason</label>
          <textarea id="resolve-reason" className="input min-h-24" maxLength={1000} value={reason}
            onChange={e => setReason(e.target.value)} aria-invalid={!!error} aria-describedby={error ? 'resolve-error' : undefined} />
          {error && <p id="resolve-error" role="alert" className="mt-1 text-xs text-rose-700">{error}</p>}
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" className={purpleOutline} onClick={() => dialog.current?.close()}>Cancel</button>
          <button type="submit" className={purpleButton} disabled={resolve.isPending}>
            {resolve.isPending ? 'Resolving…' : 'Confirm resolution'}
          </button>
        </div>
      </form>
    </dialog>
  );
}
