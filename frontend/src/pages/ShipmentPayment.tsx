import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { loadStripe } from '@stripe/stripe-js/pure';
import type { Stripe, StripeCardElement } from '@stripe/stripe-js';
import { PaymentsApi } from '../api/payments';
import type { StripeIntent } from '../api/payments';
import { ShipmentPriceSummary } from '../components/ShipmentRequestSummary';
import { useAuth } from '../auth/AuthContext';
import { money } from './Marketplace';

function paymentError(error: unknown) {
  if (isAxiosError<{ message?: string }>(error)) return error.response?.data?.message || 'Payment service unavailable. Please retry.';
  return error instanceof Error ? error.message : 'Payment could not be completed. Please retry.';
}

export function ShipmentPayment() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const qc = useQueryClient();
  const [submitted, setSubmitted] = useState(false);
  const query = useQuery({ queryKey: ['marketplace', 'payment', id, user?.id],
    queryFn: () => PaymentsApi.status(id), refetchInterval: 3000 });
  const start = useMutation({ mutationFn: () => PaymentsApi.start(id) });
  const payment = query.data;
  useEffect(() => {
    if (payment?.status === 'SUCCEEDED' || payment?.status === 'REFUNDED') {
      qc.invalidateQueries({ queryKey: ['marketplace', 'request', id] });
    }
  }, [payment?.status, id, qc]);
  return <div className="mx-auto max-w-3xl space-y-5">
    <Link className="underline" to={`/marketplace/requests/${id}`}>Back to package</Link>
    <h1 className="text-3xl font-bold">Shipment payment</h1>
    <p role="note" className="rounded-xl border border-amber-300 bg-amber-50 p-4">
      Stripe test mode only. No real money is charged, held, refunded, or paid out. Do not enter real card details.
    </p>
    {query.isPending && <p role="status">Loading accepted shipment and payment...</p>}
    {query.isError && <p role="alert">{paymentError(query.error)} <button className="underline" onClick={() => query.refetch()}>Retry payment status</button></p>}
    {payment && <section className="card space-y-4">
      <h2 className="text-xl font-bold">Accepted order summary</h2>
      <p className="break-words">{payment.quote.origin} &rarr; {payment.quote.destination}</p>
      <p>Departure: {payment.quote.departureDate}</p>
      <ShipmentPriceSummary quote={payment.quote} payment />
      <p className="font-semibold">Test payment status: {payment.status ?? 'NOT STARTED'}</p>
      {payment.status === 'SUCCEEDED' && <p role="status" className="rounded-xl bg-emerald-50 p-4 text-emerald-900">
        Test payment confirmed by the server's verified Stripe webhook. No real funds or carrier payout.
        {' '}<Link className="underline font-semibold" to={`/marketplace/requests/${id}`}>Open booking chat</Link>
      </p>}
      {payment.status === 'REFUNDED' && <p role="status">This test payment was refunded. No real money moved.</p>}
      {payment.status === 'CANCELLED' && <p role="status">This test payment was cancelled. Return to the package to cancel the booking.</p>}
      {!payment.configured && <p role="alert">Stripe test payments are not configured. An administrator must configure test keys and the webhook before you can pay.</p>}
      {payment.canPay && payment.configured && !submitted && <>
        {!start.data && <button className="btn btn-primary" disabled={start.isPending}
          onClick={() => start.mutate()}>{start.isPending ? 'Preparing secure test payment...' : 'Continue to secure test card form'}</button>}
        {start.isError && <p role="alert">{paymentError(start.error)}</p>}
        {start.data && <TestCardForm key={start.data.clientSecret} intent={start.data} total={payment.quote.total}
          onSubmitted={() => { setSubmitted(true); query.refetch(); }} />}
      </>}
      {submitted && payment.status === 'PENDING' && <p role="status">
        Card confirmation submitted. Waiting for the verified Stripe webhook. Do not pay again. This page checks automatically;
        if confirmation is delayed, return later or contact an administrator.
      </p>}
      {!payment.canPay && payment.status == null && <p role="status">This booking is not payable in its current state.</p>}
    </section>}
  </div>;
}

function TestCardForm({ intent, total, onSubmitted }: { intent: StripeIntent; total: number; onSubmitted: () => void }) {
  const container = useRef<HTMLDivElement>(null);
  const stripe = useRef<Stripe | null>(null);
  const card = useRef<StripeCardElement | null>(null);
  const [ready, setReady] = useState(false);
  const [complete, setComplete] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setError('');
    setReady(false);
    setComplete(false);
    const initialize = async () => {
      try {
        if (!intent.testMode || !intent.publishableKey.startsWith('pk_test_')) throw new Error('Only Stripe test keys are supported.');
        const client = await loadStripe(intent.publishableKey);
        if (!client) throw new Error('Stripe card form could not load. Check your connection and retry.');
        if (!active || !container.current) return;
        stripe.current = client;
        const element = client.elements().create('card');
        card.current = element;
        element.on('ready', () => { if (active) setReady(true); });
        element.on('change', event => { if (active) { setComplete(event.complete); setError(event.error?.message ?? ''); } });
        element.mount(container.current);
      } catch (e) {
        if (active) setError(paymentError(e));
      }
    };
    initialize();
    return () => { active = false; card.current?.destroy(); card.current = null; stripe.current = null; };
  }, [intent.publishableKey, intent.testMode, attempt]);
  return <form className="space-y-4 border-t pt-4" onSubmit={async e => {
    e.preventDefault();
    if (!stripe.current || !card.current || !complete || busy) return;
    setBusy(true);
    setError('');
    try {
      const result = await stripe.current.confirmCardPayment(intent.clientSecret, { payment_method: { card: card.current } });
      if (result.error) setError(result.error.message ?? 'Test card confirmation failed. Review your card and retry.');
      else if (result.paymentIntent && ['succeeded', 'processing'].includes(result.paymentIntent.status)) onSubmitted();
      else setError('Payment is not confirmed yet. Review your card and retry.');
    } catch (err) {
      setError(paymentError(err));
    } finally {
      setBusy(false);
    }
  }}>
    <h3 className="font-bold" id="test-card-label">Test card details</h3>
    <p className="text-sm">Use Stripe test card 4242 4242 4242 4242, any future expiry and any 3-digit CVC. Card details go directly to Stripe, not our server.</p>
    <div ref={container} aria-labelledby="test-card-label" className="rounded-xl border border-ink-300 bg-white p-4" />
    {!ready && !error && <p role="status">Loading Stripe's secure card form...</p>}
    {error && <p role="alert">{error} {!ready && <button type="button" className="underline" onClick={() => setAttempt(n => n + 1)}>Retry card form</button>}</p>}
    <button className="btn btn-primary" disabled={!ready || !complete || busy} type="submit">
      {busy ? 'Confirming test payment...' : `Pay ${money(total)} in test mode`}
    </button>
    {busy && <p role="status">Complete any verification in the Stripe dialog. Do not close this page.</p>}
  </form>;
}
