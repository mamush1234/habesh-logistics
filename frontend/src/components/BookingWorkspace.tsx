import { useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import type { MarketRequest } from '../api/marketplace';
import { useAuth } from '../auth/AuthContext';
import { display, money } from '../pages/Marketplace';
import { PackageChat } from './PackageChat';
import { PackageContentsSummary } from './PackageItemsEditor';
import { DeliveryTimeline } from './DeliveryTimeline';

const sections = [
  { id: 'chat', label: 'Chat' },
  { id: 'tracking', label: 'Tracking' },
  { id: 'package', label: 'Package Details' },
  { id: 'payment', label: 'Payment' },
] as const;
type Section = typeof sections[number]['id'];

export function BookingWorkspace({ request }: { request: MarketRequest }) {
  const { user } = useAuth();
  const id = useId();
  const navigation = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState<Section>('chat');
  const paid = request.escrowStatus !== 'NONE';
  const closed = request.status === 'CANCELLED' || request.status === 'COMPLETED';
  const sender = user?.id === request.senderId;
  const recent = [...request.timeline].reverse().sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)).slice(0, 5);
  return <section className="card space-y-5" aria-labelledby={`${id}-heading`}>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h2 id={`${id}-heading`} className="text-2xl font-bold">Booking workspace</h2>
      <span className="rounded-full bg-emerald-100 px-3 py-1 text-sm font-bold text-emerald-900">{display(request.status)}</span>
    </div>
    <div className="grid gap-5 md:grid-cols-[11rem_minmax(0,1fr)]">
      <div className="space-y-5 min-w-0">
        <div ref={navigation} role="tablist" aria-label="Booking sections" aria-orientation="vertical"
          className="grid grid-cols-2 gap-2 md:grid-cols-1" onKeyDown={event => {
            const current = sections.findIndex(section => section.id === active);
            let next: number;
            switch (event.key) {
              case 'ArrowDown': case 'ArrowRight': next = (current + 1) % sections.length; break;
              case 'ArrowUp': case 'ArrowLeft': next = (current + sections.length - 1) % sections.length; break;
              case 'Home': next = 0; break;
              case 'End': next = sections.length - 1; break;
              default: return;
            }
            event.preventDefault();
            setActive(sections[next].id);
            navigation.current?.querySelector<HTMLButtonElement>(`[data-section="${sections[next].id}"]`)?.focus();
          }}>
          {sections.map(section => <button key={section.id} id={`${id}-${section.id}-tab`} role="tab"
            data-section={section.id} aria-selected={active === section.id} aria-controls={`${id}-${section.id}-panel`}
            tabIndex={active === section.id ? 0 : -1} onClick={() => setActive(section.id)}
            className={`rounded-xl px-3 py-3 text-left font-semibold ${active === section.id
              ? 'bg-emerald-700 text-white' : 'bg-ink-100 hover:bg-emerald-50'}`}>{section.label}</button>)}
        </div>
        <aside aria-labelledby={`${id}-activity`} className="space-y-3">
          <h3 id={`${id}-activity`} className="font-bold">Recent activities</h3>
          {recent.length === 0 && <p className="text-sm">No booking activities yet.</p>}
          <ol className="space-y-3">{recent.map((event, index) => <li key={index} className="text-sm break-words">
            <p className="font-semibold">{display(event.status)}</p>
            <p>{event.description}</p>
            <time className="text-xs text-ink-500" dateTime={event.createdAt}>{new Date(event.createdAt).toLocaleString()}</time>
          </li>)}</ol>
        </aside>
      </div>
      <div className="min-w-0">
        <div id={`${id}-chat-panel`} role="tabpanel" aria-labelledby={`${id}-chat-tab`} hidden={active !== 'chat'} tabIndex={0}>
          {paid ? <PackageChat key={`${request.id}:${user?.id}`} requestId={request.id} closed={closed} /> : <div className="rounded-xl bg-amber-50 p-4 space-y-3">
            <h3 className="text-xl font-bold">Chat locked until payment</h3>
            <p role="note">Offer acceptance alone does not open chat. {request.targetedShipment
              ? 'The server must confirm Stripe test payment through the verified webhook.'
              : 'This demo booking must be funded using the existing escrow simulation. No real money is charged.'}</p>
            {closed ? <p>This booking closed without confirmed payment. Chat is unavailable.</p>
              : sender && request.targetedShipment && request.status === 'MATCHED'
                ? <Link className="btn btn-primary" to={`/marketplace/requests/${request.id}/payment`}>Open Stripe test payment</Link>
                : <p>{sender ? 'Use Delivery actions below to fund this demo booking.' : 'Waiting for the sender to complete payment.'}</p>}
          </div>}
        </div>
        <div id={`${id}-tracking-panel`} role="tabpanel" aria-labelledby={`${id}-tracking-tab`} hidden={active !== 'tracking'} tabIndex={0}>
          <DeliveryTimeline request={request} />
        </div>
        <div id={`${id}-package-panel`} role="tabpanel" aria-labelledby={`${id}-package-tab`} hidden={active !== 'package'} tabIndex={0} className="space-y-3">
          <h3 className="text-xl font-bold">Package Details</h3>
          <p className="break-words font-semibold">{request.pickupCity} &rarr; {request.destinationCity}</p>
          <p>{request.weightKg} kg &middot; {display(request.itemType)}</p>
          <p>Available {request.availableFrom} &middot; Deadline {request.deadline}</p>
          <p className="whitespace-pre-wrap break-words">{request.description}</p>
          {request.items && request.items.length > 0 && <PackageContentsSummary items={request.items} receiver={request.receiver} />}
          <a className="underline" href="#package-overview">View package overview and contacts</a>
        </div>
        <div id={`${id}-payment-panel`} role="tabpanel" aria-labelledby={`${id}-payment-tab`} hidden={active !== 'payment'} tabIndex={0} className="space-y-3">
          <h3 className="text-xl font-bold">Payment</h3>
          <p className="rounded-xl bg-amber-50 p-3">{request.targetedShipment
            ? 'Stripe test mode only. No real money is charged or paid out.'
            : 'Demo escrow simulation only. No real money is charged or paid out.'}</p>
          <p>Payment state: <strong>{display(request.escrowStatus)}</strong></p>
          {request.escrowAmount != null && <p>Package amount: {money(request.escrowAmount)} (not the total including fees)</p>}
          {sender && request.targetedShipment && <Link className="btn btn-primary" to={`/marketplace/requests/${request.id}/payment`}>View test payment and fee summary</Link>}
          {user?.role === 'ADMIN' && request.targetedShipment && <Link className="underline" to="/admin/payments">Review admin payment records</Link>}
          {!request.targetedShipment && !paid && !closed && sender && <p>Use Delivery actions below to simulate escrow funding.</p>}
          <p className="text-sm text-ink-500">Contact details unlock after confirmed payment. Completing delivery does not trigger a real carrier payout in test/demo mode.</p>
        </div>
      </div>
    </div>
  </section>;
}
