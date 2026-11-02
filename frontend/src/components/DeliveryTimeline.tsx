import { Check, Circle } from 'lucide-react';
import type { DeliveryStatus, MarketRequest } from '../api/marketplace';
import { display } from '../pages/Marketplace';

const milestones = [
  { status: 'PAID_ESCROW', label: 'Booking Confirmed', description: 'Payment confirmed for the accepted booking.' },
  { status: 'PICKED_UP', label: 'Package Received', description: 'The carrier has received the package.' },
  { status: 'IN_TRANSIT', label: 'In Transit', description: 'The package is on its way to the destination.' },
  { status: 'ARRIVED', label: 'Arrived', description: 'The carrier recorded arrival at the destination.' },
  { status: 'DELIVERED', label: 'Delivered', description: 'The carrier recorded delivery to the recipient.' },
] satisfies { status: DeliveryStatus; label: string; description: string }[];

export function DeliveryTimeline({ request }: { request: MarketRequest }) {
  const recorded = milestones.map(step => request.timeline.find(event => event.status === step.status));
  const furthest = recorded.reduce((last, event, index) => event ? index : last, -1);
  const completed = request.status === 'COMPLETED';
  const cancelled = request.status === 'CANCELLED';
  const disputed = request.status === 'DISPUTED';
  const completion = request.timeline.find(event => event.status === 'COMPLETED');
  return <section className="space-y-5" aria-label="Shipment tracking">
    <div>
      <h3 className="text-xl font-bold">Shipment tracking</h3>
      <p className="text-sm break-words">{request.pickupCity} &rarr; {request.destinationCity}</p>
      <p className="text-sm text-ink-500">Recorded delivery updates, not live GPS. Times are shown in your local timezone.</p>
    </div>
    {cancelled ? <p role="status" className="rounded-xl bg-red-50 p-4 text-red-800">
      Shipment cancelled. No further delivery updates are expected. Recorded history is preserved.
    </p> : disputed ? <p role="status" className="rounded-xl bg-amber-50 p-4 text-amber-900">
      Shipment disputed. Progress is on hold until an administrator resolves the dispute.
    </p> : completed ? <div role="status" className="rounded-xl bg-emerald-50 p-4 text-emerald-900 space-y-1">
      <p className="font-bold">Completion confirmed</p>
      {completion && <p><time dateTime={completion.createdAt}>{new Date(completion.createdAt).toLocaleString()}</time></p>}
      <p className="text-sm">The booking is completed. This is separate from the carrier's delivery update.</p>
    </div> : request.status === 'MATCHED' ? <p role="status" className="rounded-xl bg-amber-50 p-4">
      Booking accepted; awaiting confirmed payment before pickup.
    </p> : null}
    {request.timeline.length === 0 && <p role="status">No tracking updates recorded yet.</p>}
    <ol aria-label="Shipment milestones" className="space-y-4">
      {milestones.map((step, index) => {
        const event = recorded[index];
        const current = !!event && request.status === step.status;
        const label = event ? current ? 'Current stage' : 'Recorded'
          : index < furthest || completed ? 'Not recorded' : cancelled ? 'Not reached' : 'Pending';
        return <li key={step.status} aria-current={current ? 'step' : undefined}
          className={`flex gap-3 rounded-xl border p-4 ${event ? 'border-emerald-200 bg-emerald-50' : 'border-ink-200'}`}>
          <span aria-hidden="true" className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${event
            ? 'bg-emerald-700 text-white' : 'bg-ink-100 text-ink-500'}`}>
            {event ? <Check className="h-4 w-4" /> : <Circle className="h-4 w-4" />}
          </span>
          <div className="min-w-0 flex-1 space-y-1">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 className="font-bold">{step.label}</h4>
              <span className={`rounded-full px-2 py-1 text-xs font-semibold ${event ? 'bg-emerald-100 text-emerald-900' : 'bg-ink-100 text-ink-600'}`}>{label}</span>
            </div>
            <p className="text-sm">{step.description}</p>
            {event && <p className="text-sm font-semibold"><time dateTime={event.createdAt}>{new Date(event.createdAt).toLocaleString()}</time></p>}
          </div>
        </li>;
      })}
    </ol>
    <p className="text-xs text-ink-500">Only recorded milestones have timestamps. Older bookings may not have a separate arrival update.</p>
    {request.timeline.length > 0 && <details className="rounded-xl border border-ink-200 p-4">
      <summary className="cursor-pointer font-bold">Tracking history ({request.timeline.length})</summary>
      <ol aria-label="Tracking history" className="mt-4 space-y-3">{request.timeline.map((event, index) => <li key={index} className="border-l-2 border-emerald-300 pl-4 text-sm break-words">
        <strong>{display(event.status)}</strong>
        <p><time dateTime={event.createdAt}>{new Date(event.createdAt).toLocaleString()}</time></p>
        <p className="whitespace-pre-wrap">{event.description}</p>
      </li>)}</ol>
    </details>}
  </section>;
}
