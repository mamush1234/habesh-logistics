import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ShipmentApi } from '../api/endpoints';
import { RecipientDetails } from './RecipientDetails';
import { PackagePhoto } from './PackagePhoto';
import type { Recipient } from '../types';

export const SHIPMENT_STATUSES = [
  'CREATED', 'PAYMENT_PENDING', 'READY_FOR_PICKUP', 'PICKUP_SCHEDULED', 'PICKED_UP',
  'RECEIVED_AT_ALEXANDRIA', 'READY_FOR_CONSOLIDATION', 'ASSIGNED_TO_LUGGAGE', 'ASSIGNED_TO_TRIP', 'HANDED_TO_TRAVELER',
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
      qc.invalidateQueries({ queryKey: ['trip'] });
      qc.invalidateQueries({ queryKey: ['luggage'] });
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

export function AssignedShipments({ shipments, showRecipientDetails = false }: {
  shipments?: { id: string; trackingNumber: string; destinationCity: string; declaredWeightKg: number | string; status: string; hasPhoto?: boolean; recipient: Recipient; recipients: Recipient[]; description?: string }[];
  showRecipientDetails?: boolean;
}) {
  return (
    <div className="card">
      <h2 className="text-xl font-semibold mb-3">Shipments assigned to me</h2>
      <table className="w-full text-sm">
        <thead className="text-xs text-ink-500">
          <tr><th className="text-left py-1">Tracking</th>{showRecipientDetails && <th className="text-left">Recipient / description</th>}<th className="text-left">Destination</th><th className="text-left">Weight</th><th className="text-left">Photo</th><th className="text-left">Status</th></tr>
        </thead>
        <tbody className="divide-y divide-ink-300/50">
          {shipments?.map(s => (
            <tr key={s.id}>
              <td className="py-2 font-mono">{s.trackingNumber}</td>
              {showRecipientDetails && <td className="py-2"><RecipientDetails name={s.recipient.name} phone={s.recipient.phone} recipients={s.recipients} description={s.description} /></td>}
              <td>{s.destinationCity}</td>
              <td>{s.declaredWeightKg} kg</td>
              <td>
                <PackagePhoto id={s.id} trackingNumber={s.trackingNumber} hasPhoto={!!s.hasPhoto} />
              </td>
              <td><ShipmentStatusSelect id={s.id} value={s.status} testId={`status-${s.trackingNumber}`} /></td>
            </tr>
          ))}
          {shipments?.length === 0 && <tr><td colSpan={showRecipientDetails ? 6 : 5} className="py-2 text-ink-500">Nothing assigned yet.</td></tr>}
        </tbody>
      </table>
    </div>
  );
}
