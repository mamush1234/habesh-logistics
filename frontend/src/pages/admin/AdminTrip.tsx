import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { TripApi, LuggageApi, ShipmentApi, AdminApi, qrUrl } from '../../api/endpoints';
import { StatusBadge } from '../../components/StatusBadge';
import { toast } from 'sonner';
import { Zap, QrCode } from 'lucide-react';

export function AdminTrip() {
  const { id } = useParams();
  const qc = useQueryClient();
  const trip = useQuery({ queryKey: ['trip', id], queryFn: () => TripApi.get(id!), enabled: !!id });
  const shipments = useQuery({ queryKey: ['shipments'], queryFn: ShipmentApi.list });
  const agents = useQuery({ queryKey: ['admin-users', 'AGENT'], queryFn: () => AdminApi.users('AGENT') });

  const travelers = useQuery({ queryKey: ['admin-users', 'TRAVELER'], queryFn: () => AdminApi.users('TRAVELER') });
  const travelerMut = useMutation({
    mutationFn: ({ luggageId, travelerId }: { luggageId: string; travelerId: string | null }) =>
      LuggageApi.assignTraveler(luggageId, travelerId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['trip', id] });
      toast.success('Traveler updated');
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed to update traveler'),
  });
  const agentMut = useMutation({
    mutationFn: ({ luggageId, agentId }: { luggageId: string; agentId: string | null }) =>
      LuggageApi.assignAgent(luggageId, agentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['trip', id] });
      toast.success('Agent updated');
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed to update agent'),
  });
  const assignMut = useMutation({
    mutationFn: ({ luggageId, shipmentId }: { luggageId: string; shipmentId: string }) =>
      LuggageApi.assign(luggageId, shipmentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['trip', id] });
      qc.invalidateQueries({ queryKey: ['shipments'] });
      toast.success('Assigned');
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Assign failed'),
  });
  const unassignMut = useMutation({
    mutationFn: ({ luggageId, shipmentId }: { luggageId: string; shipmentId: string }) =>
      LuggageApi.unassign(luggageId, shipmentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['trip', id] });
      qc.invalidateQueries({ queryKey: ['shipments'] });
      toast.success('Removed');
    },
  });
  const autoMut = useMutation({
    mutationFn: () => LuggageApi.autoAssign(id!),
    onSuccess: (d) => {
      qc.invalidateQueries({ queryKey: ['trip', id] });
      qc.invalidateQueries({ queryKey: ['shipments'] });
      toast.success(`${d.assigned} shipment(s) auto-assigned`);
    },
  });

  if (trip.isLoading) return <p>Loading…</p>;
  if (!trip.data) return <p>Not found</p>;
  const t = trip.data;

  // Available = shipments not assigned to any luggage, received at Alexandria
  const available = (shipments.data ?? []).filter(s => !s.luggageId);

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between flex-wrap gap-3">
        <div>
          <div className="text-xs uppercase tracking-widest text-ink-500">Trip</div>
          <h1 className="text-3xl font-bold font-display">{t.tripNumber}</h1>
          <p className="text-ink-500">Traveler: {t.travelerName} · {t.departureAirport} → {t.arrivalAirport} · {new Date(t.departureDate).toLocaleDateString()}</p>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge value={t.status} />
          <button className="btn btn-brand" onClick={() => autoMut.mutate()} data-testid="auto-assign"><Zap className="w-4 h-4" /> Auto-assign</button>
        </div>
      </div>

      <div className="grid md:grid-cols-2 gap-4">
        {t.luggage.map(l => (
          <div className="card space-y-3" key={l.id} data-testid={`admin-luggage-${l.luggageNumber}`}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="font-bold">{l.luggageNumber}</div>
                <div className="text-sm text-ink-500">Capacity {l.capacityKg} kg · Assigned {l.assignedWeightKg} kg · Available {l.availableCapacityKg} kg</div>
              </div>
              <a className="btn btn-ghost text-xs" href={qrUrl.luggage(l.id)} target="_blank" rel="noreferrer"><QrCode className="w-3.5 h-3.5" /> QR</a>
            </div>
            <div>
              <label className="text-xs text-ink-500">Carrying traveler (overrides trip traveler)</label>
              <select className="input mt-1" value={l.assignedTravelerId ?? ''}
                onChange={e => travelerMut.mutate({ luggageId: l.id, travelerId: e.target.value || null })}
                data-testid={`luggage-traveler-${l.luggageNumber}`}>
                <option value="">Trip traveler ({t.travelerName})</option>
                {travelers.data?.map(a => <option key={a.id} value={a.id}>{a.firstName} {a.lastName}</option>)}
              </select>
            </div>
            <div>
              <label className="text-xs text-ink-500">Receiving agent (Ethiopia)</label>
              <select className="input mt-1" value={l.assignedAgentId ?? ''}
                onChange={e => agentMut.mutate({ luggageId: l.id, agentId: e.target.value || null })}
                data-testid={`luggage-agent-${l.luggageNumber}`}>
                <option value="">Unassigned</option>
                {agents.data?.map(a => <option key={a.id} value={a.id}>{a.firstName} {a.lastName}</option>)}
              </select>
            </div>
            <ul className="text-sm divide-y divide-ink-300/50">
              {l.shipments.map(s => (
                <li className="py-2 flex items-center justify-between" key={s.id}>
                  <span className="font-mono">{s.trackingNumber}</span>
                  <span>{s.declaredWeightKg} kg · {s.destinationCity}</span>
                  <button className="btn btn-ghost !py-1 !px-2 text-[11px]" onClick={() => unassignMut.mutate({ luggageId: l.id, shipmentId: s.id })}>Remove</button>
                </li>
              ))}
              {l.shipments.length === 0 && <li className="py-2 text-ink-500 text-xs">Empty</li>}
            </ul>
            <div>
              <div className="text-xs text-ink-500 mb-1">Assign a shipment:</div>
              <div className="flex gap-2 flex-wrap">
                {available.map(s => (
                  <button key={s.id} className="btn btn-ghost text-xs"
                    onClick={() => assignMut.mutate({ luggageId: l.id, shipmentId: s.id })}
                    data-testid={`assign-${s.trackingNumber}-to-${l.luggageNumber}`}>
                    + {s.trackingNumber} ({s.declaredWeightKg} kg)
                  </button>
                ))}
                {available.length === 0 && <span className="text-xs text-ink-500">All shipments are assigned.</span>}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
