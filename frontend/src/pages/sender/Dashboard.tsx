import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ShipmentApi } from '../../api/endpoints';
import { StatusBadge } from '../../components/StatusBadge';
import { Package } from 'lucide-react';

export function SenderDashboard() {
  const q = useQuery({ queryKey: ['my-shipments'], queryFn: ShipmentApi.list });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold">My shipments</h1>
        <Link to="/send" className="btn btn-brand"><Package className="w-4 h-4" /> New shipment</Link>
      </div>

      {q.isLoading && <p className="text-ink-500">Loading…</p>}
      {q.data && q.data.length === 0 && (
        <div className="card text-center py-10">
          <p className="text-ink-500">No shipments yet.</p>
          <Link to="/send" className="btn btn-brand mt-3 inline-flex">Send your first package</Link>
        </div>
      )}

      <div className="grid md:grid-cols-2 gap-4" data-testid="sender-shipments">
        {q.data?.map(s => (
          <Link to={`/track/${s.trackingNumber}`} className="card hover:shadow-md transition" key={s.id} data-testid={`shipment-${s.trackingNumber}`}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-xs uppercase tracking-widest text-ink-500">Tracking</div>
                <div className="text-xl font-bold font-display">{s.trackingNumber}</div>
              </div>
              <StatusBadge value={s.status} />
            </div>
            <div className="grid grid-cols-2 gap-3 mt-4 text-sm">
              <div><div className="text-ink-500 text-xs">To</div><div className="font-semibold">{s.destinationCity}</div></div>
              <div><div className="text-ink-500 text-xs">Weight</div><div className="font-semibold">{s.declaredWeightKg} kg</div></div>
              <div><div className="text-ink-500 text-xs">Receiver</div><div className="font-semibold">{s.recipient.name}</div></div>
              <div><div className="text-ink-500 text-xs">Total</div><div className="font-semibold">${s.totalAmount}</div></div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
