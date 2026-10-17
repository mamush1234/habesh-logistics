import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ShipmentApi } from '../../api/endpoints';
import { StatusBadge } from '../../components/StatusBadge';
import { Package } from 'lucide-react';
import { PackagePhoto } from '../../components/PackagePhoto';
import { RecipientDetails } from '../../components/RecipientDetails';
import { TravelCalendar } from '../../components/TravelCalendar';

export function SenderDashboard() {
  const q = useQuery({ queryKey: ['my-shipments'], queryFn: ShipmentApi.list, refetchInterval: 30_000 });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold">Legacy operational shipments</h1>
        <Link to="/marketplace" className="btn btn-brand"><Package className="w-4 h-4" /> Delivery marketplace</Link>
      </div>
      <p className="text-sm text-ink-500">Existing shipment records remain here. New requests and offers are in the delivery marketplace.</p>

      {q.isLoading && <p className="text-ink-500">Loading…</p>}
      {q.isError && <p role="alert" className="text-sm text-red-700">
        Could not load your packages. <button type="button" className="underline" onClick={() => q.refetch()}>Retry</button>
      </p>}
      {q.data && q.data.length === 0 && (
        <div className="card text-center py-10">
          <p className="text-ink-500">No shipments yet.</p>
          <Link to="/send" className="btn btn-brand mt-3 inline-flex">Post a delivery request</Link>
        </div>
      )}

      <div className="grid md:grid-cols-2 gap-4" data-testid="sender-shipments">
        {q.data?.map(s => (
          <div className="card hover:shadow-md transition" key={s.id} data-testid={`shipment-${s.trackingNumber}`}>
          <Link to={`/track/${s.trackingNumber}`} className="block">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-xs uppercase tracking-widest text-ink-500">Tracking</div>
                <div className="text-xl font-bold font-display">{s.trackingNumber}</div>
              </div>
              <StatusBadge value={s.status} />
            </div>
            <div className="grid grid-cols-2 gap-3 mt-4 text-sm">
              <div><div className="text-ink-500 text-xs">To</div><div className="font-semibold">{s.destinationCity}, {s.destinationCountry}</div></div>
              <div><div className="text-ink-500 text-xs">Package type</div><div className="font-semibold">{s.packageType}</div></div>
              <div><div className="text-ink-500 text-xs">Weight</div><div className="font-semibold">{s.declaredWeightKg} kg</div></div>
              <div><div className="text-ink-500 text-xs">Total</div><div className="font-semibold">${s.totalAmount}</div></div>
              <div><div className="text-ink-500 text-xs">Posted</div><div className="font-semibold">{new Date(s.createdAt).toLocaleString()}</div></div>
            </div>
          </Link>
            <div className="mt-3 text-sm space-y-1">
              <p>Delivery method: {s.pickupMethod === 'PICKUP' ? 'Pickup' : 'Alexandria drop-off'}</p>
              {s.pickupMethod === 'PICKUP' && s.pickupAddress && <p>Pickup address: {s.pickupAddress}</p>}
              {s.pickupMethod === 'PICKUP' && s.pickupRequestedDate && <p>Requested pickup: {new Date(`${s.pickupRequestedDate}T00:00:00`).toLocaleDateString()}</p>}
            </div>
            {s.queueStatus && <p className="mt-3 text-sm">Package queue: <StatusBadge value={s.queueStatus} />
              {s.queueStatus === 'RESERVED' && s.queueExpiresAt && <> &middot; Review deadline: {new Date(s.queueExpiresAt).toLocaleTimeString()}</>}
            </p>}
            {s.travelAnnouncementId && <div className="mt-3 rounded-xl bg-ink-100 p-3 text-sm space-y-1">
              <p className="font-semibold">Requested travel: {s.travelRoute}</p>
              <p>{s.travelDepartureDate && new Date(`${s.travelDepartureDate}T00:00:00`).toLocaleDateString()}
                {' '}&middot; {s.declaredWeightKg} kg &middot; {s.travelBookingStatus}</p>
              {s.travelBookingStatus === 'PENDING' && <p>Waiting for agent approval. No space reserved yet.</p>}
              {s.travelBookingStatus === 'APPROVED' && <p>Your {s.declaredWeightKg} kg space is reserved.</p>}
              {s.travelBookingStatus === 'REJECTED' && <p>The agent declined this travel request. No space reserved.</p>}
              {s.travelBookingStatus === 'CANCELLED' && <p>Travel reservation cancelled. No space reserved.</p>}
            </div>}
            <div className="mt-3">
              <RecipientDetails name={s.recipient.name} phone={s.recipient.phone} recipients={s.recipients} description={s.description} />
            </div>

            <TravelCalendar />
            <div className="mt-4">
              <PackagePhoto id={s.id} trackingNumber={s.trackingNumber} hasPhoto={!!s.hasPhoto} senderId={s.senderId} />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
