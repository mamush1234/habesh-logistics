import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ShipmentApi } from '../api/endpoints';

export const SHIPMENT_STATUSES = [
  'CREATED', 'PAYMENT_PENDING', 'READY_FOR_PICKUP', 'PICKUP_SCHEDULED', 'PICKED_UP',
  'RECEIVED_AT_ALEXANDRIA', 'READY_FOR_CONSOLIDATION', 'ASSIGNED_TO_LUGGAGE', 'HANDED_TO_TRAVELER',
  'DEPARTED_US', 'ARRIVED_ETHIOPIA', 'RECEIVED_BY_AGENT', 'READY_FOR_RECIPIENT', 'DELIVERED',
  'CANCELLED', 'ON_HOLD',
];

export function ShipmentStatusSelect({ id, value, testId }: { id: string; value: string; testId?: string }) {
  const qc = useQueryClient();
  const mut = useMutation({
    mutationFn: (status: string) => ShipmentApi.updateStatus(id, status),
    onSuccess: () => {
      toast.success('Status updated');
      qc.invalidateQueries({ queryKey: ['shipments'] });
      qc.invalidateQueries({ queryKey: ['trips'] });
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed to update status'),
  });
  return (
    <select className="input !py-1" value={value} data-testid={testId}
      onChange={e => mut.mutate(e.target.value)}>
      {SHIPMENT_STATUSES.map(s => <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>)}
    </select>
  );
}

export function AssignedShipments({ shipments }: { shipments?: { id: string; trackingNumber: string; destinationCity: string; declaredWeightKg: number | string; status: string; hasPhoto?: boolean }[] }) {
  const [photo, setPhoto] = useState<string | null>(null);
  return (
    <div className="card">
      <h2 className="text-xl font-semibold mb-3">Shipments assigned to me</h2>
      <table className="w-full text-sm">
        <thead className="text-xs text-ink-500">
          <tr><th className="text-left py-1">Tracking</th><th className="text-left">Destination</th><th className="text-left">Weight</th><th className="text-left">Photo</th><th className="text-left">Status</th></tr>
        </thead>
        <tbody className="divide-y divide-ink-300/50">
          {shipments?.map(s => (
            <tr key={s.id}>
              <td className="py-2 font-mono">{s.trackingNumber}</td>
              <td>{s.destinationCity}</td>
              <td>{s.declaredWeightKg} kg</td>
              <td>
                {s.hasPhoto
                  ? <button className="btn btn-ghost text-xs" onClick={() => ShipmentApi.photo(s.id).then(setPhoto).catch(() => toast.error('Could not load photo'))}>View</button>
                  : <span className="text-ink-500">—</span>}
              </td>
              <td><ShipmentStatusSelect id={s.id} value={s.status} testId={`status-${s.trackingNumber}`} /></td>
            </tr>
          ))}
          {shipments?.length === 0 && <tr><td colSpan={5} className="py-2 text-ink-500">Nothing assigned yet.</td></tr>}
        </tbody>
      </table>
      {photo && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={() => setPhoto(null)}>
          <img src={photo} alt="Package" className="max-h-[85vh] max-w-full rounded-xl" />
        </div>
      )}
    </div>
  );
}
