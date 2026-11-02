import { useEffect, useId, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { DeliveryConfirmationApi } from '../api/deliveryConfirmation';
import type { IssuedDeliveryCode } from '../api/deliveryConfirmation';
import type { MarketRequest } from '../api/marketplace';
import { MarketplaceApi } from '../api/marketplace';
import { useAuth } from '../auth/AuthContext';

function errorMessage(error: unknown) {
  return isAxiosError<{ message?: string }>(error)
    ? error.response?.data?.message || 'Delivery confirmation unavailable. Please retry.'
    : 'Delivery confirmation unavailable. Please retry.';
}

export function DeliveryConfirmation({ request }: { request: MarketRequest }) {
  const { user } = useAuth();
  const id = useId();
  const qc = useQueryClient();
  const key = ['delivery-confirmation', request.id, user?.id];
  const [code, setCode] = useState('');
  const [revealed, setRevealed] = useState<IssuedDeliveryCode | null>(null);
  const [now, setNow] = useState(Date.now());
  const query = useQuery({ queryKey: key, queryFn: () => DeliveryConfirmationApi.status(request.id), refetchInterval: 5000 });
  const status = query.data;
  const sender = request.senderId === user?.id;
  const verifier = user?.role === 'ADMIN' || (user?.role === 'AGENT'
    && request.offers.some(o => o.id === request.acceptedOfferId && o.traveler.id === user.id));
  const invalidate = () => {
    for (const prefix of ['delivery-confirmation', 'marketplace', 'delivery-reviews', 'admin-console', 'agent', 'public-travel', 'package-chat'])
      qc.invalidateQueries({ queryKey: [prefix] });
  };
  const issue = useMutation({
    mutationFn: () => DeliveryConfirmationApi.issue(request.id),
    onSuccess: issued => { setRevealed(issued); query.refetch(); },
    onError: () => query.refetch(),
  });
  const verify = useMutation({
    mutationFn: () => DeliveryConfirmationApi.verify(request.id, code),
    onSuccess: updated => { qc.setQueryData(key, updated); setCode(''); setRevealed(null); invalidate(); },
    onError: () => query.refetch(),
  });
  const legacy = useMutation({
    mutationFn: () => MarketplaceApi.transition(request.id, 'DELIVERED'),
    onSuccess: invalidate,
  });
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    if (request.status !== 'ARRIVED' || status?.confirmedAt) setRevealed(null);
  }, [request.status, status?.confirmedAt]);
  const activeReveal = revealed && sender && now < Date.parse(revealed.expiresAt) && !query.isError
    && request.status === 'ARRIVED' && !status?.confirmedAt && status?.attemptsRemaining !== 0
    && status?.expiresAt != null && Date.parse(revealed.expiresAt) === Date.parse(status.expiresAt);
  const remaining = status?.expiresAt ? Math.max(0, Math.ceil((Date.parse(status.expiresAt) - now) / 1000)) : 0;
  return <section className="card space-y-4" aria-labelledby={`${id}-heading`}>
    <h2 id={`${id}-heading`} className="text-xl font-bold">Delivery confirmation</h2>
    <p role="note" className="rounded-xl bg-amber-50 p-3">Test/demo mode only. Code verification completes this booking and releases its simulated/test payment state. No real money is paid to a carrier.</p>
    {query.isPending && <p role="status">Loading delivery confirmation...</p>}
    {query.isError && <p role="alert">Could not load delivery confirmation. <button className="underline" onClick={() => query.refetch()}>Retry confirmation</button></p>}
    {query.isSuccess && status && <>
      {status.confirmedAt ? <div role="status" className="rounded-xl bg-emerald-50 p-4">
        <p className="font-bold">Receiver code verified. Delivery completed.</p>
        <time dateTime={status.confirmedAt}>{new Date(status.confirmedAt).toLocaleString()}</time>
        <p>Reviews are now available. No real payout was made.</p>
      </div> : !status.required ? <div className="space-y-3">
        <p>This booking uses the existing legacy delivery confirmation flow. No receiver code is required.</p>
        {request.status === 'ARRIVED' && verifier && <button className="btn btn-primary" disabled={legacy.isPending}
          onClick={() => legacy.mutate()}>{legacy.isPending ? 'Updating delivery...' : 'Mark delivered (legacy booking)'}</button>}
        {legacy.isError && <p role="alert">{errorMessage(legacy.error)}</p>}
      </div> : request.status !== 'ARRIVED' ? <p>
        {request.status === 'COMPLETED' ? 'This booking was completed without a receiver-code confirmation; see its recorded history.'
          : request.status === 'CANCELLED' || request.status === 'DISPUTED' ? 'Code issuance and verification are unavailable while this booking is cancelled or disputed.'
            : 'A funded booking must reach Arrived before a receiver delivery code can be issued or verified.'}
      </p> : <>
        {sender && <div className="space-y-3">
          <p>Reveal a code and share it privately with the receiver. The receiver should give it to the agent/admin only after receiving the package.</p>
          <p className="font-semibold">Manual sharing only. We do not send SMS or email. The code is shown only in this browser session; it is not retrievable after reload.</p>
          <button className="btn btn-primary" disabled={!status.canIssue || issue.isPending || verify.isPending}
            onClick={() => issue.mutate()}>{issue.isPending ? 'Generating code...' : status.expiresAt ? 'Generate replacement code' : 'Reveal receiver code'}</button>
          {status.retryIssueAt && !status.canIssue && <p className="text-sm">Next code can be issued after {new Date(status.retryIssueAt).toLocaleString()} if the booking is still eligible.</p>}
          {activeReveal && <div className="rounded-xl border border-emerald-300 bg-emerald-50 p-4 space-y-2">
            <p className="font-bold">Share this code only with the receiver</p>
            <output aria-label="Receiver delivery code" className="block font-mono text-3xl tracking-widest">{revealed.code}</output>
            <button className="underline" onClick={() => { setRevealed(null); issue.reset(); }}>Hide code</button>
          </div>}
          {issue.isError && <p role="alert">{errorMessage(issue.error)} If a response was lost, wait for the replacement-code window rather than assuming the code was delivered.</p>}
        </div>}
        {status.expiresAt && <p className="text-sm">{remaining > 0
          ? `Code expires in ${Math.floor(remaining / 60)}m ${remaining % 60}s.`
          : 'Code expired. Ask the sender to issue and manually share a new code.'} Attempts remaining: {status.attemptsRemaining}.</p>}
        {status.attemptsRemaining === 0 && <p role="alert">Code locked after five incorrect attempts. Wait for expiry, then ask the sender for a replacement.</p>}
        {verifier && <div className="space-y-3">
          <p>Ask the receiver for the code after handing over the package. Do not ask the sender to bypass the receiver.</p>
          {status.canVerify && remaining > 0 ? <form className="space-y-3" onSubmit={event => {
            event.preventDefault();
            if (/^[0-9]{6}$/.test(code) && !verify.isPending) verify.mutate();
          }}>
            <label htmlFor={`${id}-code`} className="label">Receiver delivery code
              <input id={`${id}-code`} className="input font-mono" inputMode="numeric" autoComplete="off" type="text"
                pattern="[0-9]{6}" minLength={6} maxLength={6} required value={code} disabled={verify.isPending}
                onChange={event => setCode(event.target.value)} />
            </label>
            <button className="btn btn-primary" disabled={verify.isPending || !/^[0-9]{6}$/.test(code)}>
              {verify.isPending ? 'Verifying delivery...' : 'Verify code and complete delivery'}
            </button>
          </form> : <p role="status">Waiting for the sender to issue an active code for the receiver.</p>}
          {verify.isError && <p role="alert">{errorMessage(verify.error)}</p>}
        </div>}
        {user?.role === 'TRAVELER' && <p>Travelers remain read-only. An administrator must enter the receiver code for this booking.</p>}
      </>}
    </>}
  </section>;
}
