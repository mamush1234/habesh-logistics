import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { LuggageApi } from '../api/endpoints';
import type { Luggage, Shipment, ShipmentSummary } from '../types';
import { StatusBadge } from './StatusBadge';
import { PackagePhoto } from './PackagePhoto';
import { RecipientDetails } from './RecipientDetails';

export function assignmentError(error: unknown, fallback: string) {
  const message: unknown = isAxiosError(error) ? error.response?.data?.message : undefined;
  return typeof message === 'string' ? message : fallback;
}

export function availablePackages(shipments: Shipment[]) {
  const eligible = ['CREATED', 'READY_FOR_PICKUP', 'PICKED_UP', 'RECEIVED_AT_ALEXANDRIA', 'READY_FOR_CONSOLIDATION'];
  return shipments.filter(s => !s.luggageId && !s.tripId && eligible.includes(s.status));
}

function PackingPackage({ shipment }: { shipment: Pick<ShipmentSummary, 'id' | 'trackingNumber' | 'senderName' | 'hasPhoto' | 'recipients' | 'description'> }) {
  return (
    <div className="flex items-center gap-3 min-w-0">
      <PackagePhoto id={shipment.id} trackingNumber={shipment.trackingNumber} hasPhoto={shipment.hasPhoto} />
      <div className="min-w-0">
        <div className="font-mono">{shipment.trackingNumber}</div>
        <div className="text-xs text-ink-500 break-words">Sender: {shipment.senderName}</div>
        <RecipientDetails name={shipment.recipients[0].name} phone={shipment.recipients[0].phone}
          recipients={shipment.recipients} description={shipment.description} />
      </div>
    </div>
  );
}

export function LuggagePacking({ luggage, shipments, tripOpen = true, twoColumns = false, tripAvailableKg }: {
  luggage: Luggage; shipments: Shipment[]; tripOpen?: boolean; twoColumns?: boolean; tripAvailableKg?: string;
}) {
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const qc = useQueryClient();
  const refresh = () => Promise.all(
    ['luggage', 'trip', 'trips', 'shipments', 'admin-stats'].map(key =>
      qc.invalidateQueries({ queryKey: [key] })));
  const assign = useMutation({
    mutationFn: (shipmentIds: string[]) => LuggageApi.assignBatch(luggage.id, shipmentIds),
    onSuccess: async () => {
      setSelectedIds([]);
      await refresh();
      toast.success('Packages consolidated into luggage');
    },
    onError: (error: unknown) => toast.error(assignmentError(error, 'Failed to consolidate package')),
  });
  const remove = useMutation({
    mutationFn: (shipmentId: string) => LuggageApi.unassign(luggage.id, shipmentId),
    onSuccess: async () => { await refresh(); toast.success('Package removed from luggage'); },
    onError: (error: unknown) => toast.error(assignmentError(error, 'Failed to remove package')),
  });
  const status = useMutation({
    mutationFn: (value: string) => LuggageApi.updateStatus(luggage.id, value),
    onSuccess: async () => { await refresh(); toast.success('Luggage updated'); },
    onError: (error: unknown) => toast.error(assignmentError(error, 'Failed to update luggage')),
  });
  const preDispatch = ['CREATED', 'PACKING', 'READY', 'SEALED'].includes(luggage.status);
  const editable = tripOpen && preDispatch && luggage.status !== 'SEALED';
  const busy = assign.isPending || remove.isPending || status.isPending;
  const available = availablePackages(shipments);
  const weightUnits = (weight: string) => Math.round(Number(weight) * 100);
  const remaining = Math.min(weightUnits(luggage.availableCapacityKg),
    tripAvailableKg === undefined ? Infinity : weightUnits(tripAvailableKg));
  const selected = available.filter(s => selectedIds.includes(s.id));
  const selectedWeight = selected.reduce((total, s) => total + weightUnits(s.declaredWeightKg), 0);
  const canAdd = editable && !busy && selected.length > 0 && selectedWeight <= remaining;
  const addPackages = (ids: string[]) => {
    if (!editable || busy) {
      toast.error('Open this luggage for packing before adding packages');
      return;
    }
    const packages = available.filter(s => ids.includes(s.id));
    if (packages.length !== new Set(ids).size || packages.length === 0) {
      toast.error('Only available unassigned packages can be added');
      return;
    }
    const weight = packages.reduce((total, s) => total + weightUnits(s.declaredWeightKg), 0);
    if (weight > remaining) {
      toast.error('Packages exceed the remaining luggage or trip capacity');
      return;
    }
    assign.mutate(ids);
  };

  return (
    <div className={twoColumns ? 'grid lg:grid-cols-2 gap-6 items-start' : 'space-y-3'}>
      <section className={`space-y-3 ${twoColumns ? 'order-2' : ''}`}
        aria-label="Selected luggage"
        onDragOver={e => {
          if (editable && !busy && e.dataTransfer.types.includes('application/x-habesh-package')) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
            setDragOver(true);
          }
        }}
        onDragLeave={e => {
          if (!(e.relatedTarget instanceof Node) || !e.currentTarget.contains(e.relatedTarget)) setDragOver(false);
        }}
        onDrop={e => {
          e.preventDefault();
          setDragOver(false);
          const id = e.dataTransfer.getData('application/x-habesh-package');
          if (id) addPackages([id]);
        }}>
      <div className={`rounded-xl border-2 border-dashed p-4 space-y-2 ${dragOver ? 'border-brand-600 bg-ink-100' : 'border-ink-300'}`}
        data-testid="luggage-drop-zone">
        <h2 className="text-xl font-semibold">{luggage.luggageNumber}</h2>
        <div className="grid grid-cols-3 gap-2 text-sm">
          <div>Capacity<div className="font-bold">{luggage.capacityKg} kg</div></div>
          <div>Packed<div className="font-bold" data-testid="packed-weight">{luggage.assignedWeightKg} kg</div></div>
          <div>Remaining<div className="font-bold" data-testid="remaining-capacity">{luggage.availableCapacityKg} kg</div></div>
        </div>
        <progress className="w-full" aria-label="Luggage capacity used"
          max={Number(luggage.capacityKg)} value={Number(luggage.assignedWeightKg)} />
        {tripAvailableKg !== undefined && <p className="text-xs text-ink-500">Trip remaining capacity: {tripAvailableKg} kg</p>}
        <p className="text-sm text-ink-500">{editable ? 'Drop a package here, or select packages on the left and click Add.' : 'This luggage is not open for packing.'}</p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge value={luggage.status} />
        {tripOpen && preDispatch && <>
          <button className="btn btn-ghost text-xs" disabled={busy || luggage.status === 'PACKING'}
            onClick={() => status.mutate('PACKING')}>Open for packing</button>
          <button className="btn btn-ghost text-xs" disabled={busy || !editable}
            onClick={() => status.mutate('READY')}>Mark ready</button>
          <button className="btn btn-ghost text-xs" disabled={busy || !editable}
            onClick={() => status.mutate('SEALED')}>Seal luggage</button>
        </>}
      </div>
      <ul className="text-sm divide-y divide-ink-300/50">
        {luggage.shipments.map(s => (
          <li className="py-2 flex flex-wrap items-center justify-between gap-2" key={s.id}>
            <PackingPackage shipment={s} />
            <span>{s.declaredWeightKg} kg · {s.destinationCity}</span>
            {editable && <button className="btn btn-ghost !py-1 !px-2 text-[11px]" disabled={busy}
              onClick={() => remove.mutate(s.id)}>Remove</button>}
          </li>
        ))}
        {luggage.shipments.length === 0 && <li className="py-2 text-ink-500 text-xs">No packages consolidated yet.</li>}
      </ul>
      </section>
      <section className={`space-y-3 ${twoColumns ? 'order-1' : ''}`} aria-label="Available packages">
        <h2 className="text-xl font-semibold">Available packages</h2>
        <div className="rounded-lg bg-ink-100 p-3 space-y-2">
          <p className="text-sm">{selected.length} selected · {(selectedWeight / 100).toFixed(2)} kg</p>
          <p className="text-xs text-ink-500">After adding: {((weightUnits(luggage.assignedWeightKg) + selectedWeight) / 100).toFixed(2)} / {luggage.capacityKg} kg</p>
          {selectedWeight > remaining && <p role="alert" className="text-sm text-red-600">Selection exceeds available luggage or trip capacity.</p>}
          <button className="btn btn-brand" disabled={!canAdd} onClick={() => addPackages(selected.map(s => s.id))}
            data-testid="add-selected-packages">{assign.isPending ? 'Adding...' : '+ Add selected to luggage'}</button>
        </div>
        <div className="space-y-2">
          {available.map(s => (
            <div key={s.id} className="rounded-lg ring-1 ring-ink-300 p-3 flex flex-wrap items-center justify-between gap-3"
              draggable={editable && !busy && weightUnits(s.declaredWeightKg) <= remaining}
              onDragStart={e => {
                e.dataTransfer.setData('application/x-habesh-package', s.id);
                e.dataTransfer.effectAllowed = 'copy';
              }} onDragEnd={() => setDragOver(false)} data-testid={`available-package-${s.trackingNumber}`}>
              <input type="checkbox" aria-label={`Select ${s.trackingNumber}`} checked={selectedIds.includes(s.id)}
                disabled={!editable || busy}
                onChange={e => setSelectedIds(ids => e.target.checked ? [...ids, s.id] : ids.filter(id => id !== s.id))} />
              <PackingPackage shipment={{ ...s, hasPhoto: !!s.hasPhoto }} />
              <div className="text-xs text-ink-500">{s.declaredWeightKg} kg · {s.destinationCity}</div>
              <button className="btn btn-ghost text-xs"
                disabled={!editable || busy || weightUnits(s.declaredWeightKg) > remaining}
                onClick={() => addPackages([s.id])}
                data-testid={`assign-${s.trackingNumber}-to-${luggage.luggageNumber}`}>
                + Add package
              </button>
            </div>
          ))}
          {available.length === 0 && <span className="text-xs text-ink-500">No eligible unassigned packages.</span>}
        </div>
      </section>
    </div>
  );
}
