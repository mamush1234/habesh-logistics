import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { TripApi, LuggageApi, ShipmentApi, AdminApi } from '../../api/endpoints';
import { PrivateQr } from '../../components/PrivateQr';
import { StatusBadge } from '../../components/StatusBadge';
import { assignmentError, availablePackages, LuggagePacking } from '../../components/LuggagePacking';
import { toast } from 'sonner';
import { Zap } from 'lucide-react';

export function AdminTrip() {
  const { id } = useParams();
  const [selectedLuggageId, setSelectedLuggageId] = useState('');
  const [selectedPackageId, setSelectedPackageId] = useState('');
  const qc = useQueryClient();
  const trip = useQuery({ queryKey: ['trip', id], queryFn: () => TripApi.get(id!), enabled: !!id });
  const shipments = useQuery({ queryKey: ['shipments'], queryFn: ShipmentApi.list });
  const agents = useQuery({ queryKey: ['admin-users', 'AGENT'], queryFn: () => AdminApi.users('AGENT') });
  const luggage = useQuery({ queryKey: ['luggage'], queryFn: LuggageApi.list });
  const refresh = () => {
    for (const key of ['trip', 'trips', 'luggage', 'shipments', 'admin-stats']) {
      qc.invalidateQueries({ queryKey: [key] });
    }
  };

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
  const attachMut = useMutation({
    mutationFn: (luggageId: string) => LuggageApi.assignTrip(luggageId, id!),
    onSuccess: () => { setSelectedLuggageId(''); refresh(); toast.success('Luggage assigned to trip'); },
    onError: (error: unknown) => toast.error(assignmentError(error, 'Failed to assign luggage')),
  });
  const detachMut = useMutation({
    mutationFn: (luggageId: string) => LuggageApi.assignTrip(luggageId, null),
    onSuccess: () => { refresh(); toast.success('Luggage removed from trip; packages preserved'); },
    onError: (error: unknown) => toast.error(assignmentError(error, 'Failed to remove luggage from trip')),
  });
  const directMut = useMutation({
    mutationFn: ({ shipmentId, tripId }: { shipmentId: string; tripId: string | null }) =>
      ShipmentApi.assignTrip(shipmentId, tripId),
    onSuccess: () => { setSelectedPackageId(''); refresh(); toast.success('Package trip updated'); },
    onError: (error: unknown) => toast.error(assignmentError(error, 'Failed to update package trip')),
  });
  const autoMut = useMutation({
    mutationFn: () => LuggageApi.autoAssign(id!),
    onSuccess: (d) => {
      refresh();
      toast.success(`${d.assigned} shipment(s) auto-assigned`);
    },
    onError: (error: unknown) => toast.error(assignmentError(error, 'Auto-assignment failed')),
  });

  if (trip.isLoading) return <p>Loading…</p>;
  if (trip.isError) return <p role="alert">Failed to load trip. Refresh to try again.</p>;
  if (!trip.data) return <p>Not found</p>;
  const t = trip.data;

  const available = availablePackages(shipments.data ?? []);
  const tripOpen = ['OPEN', 'FILLING', 'READY'].includes(t.status);
  const unassignedLuggage = (luggage.data ?? []).filter(l => !l.tripId
    && ['CREATED', 'PACKING', 'READY', 'SEALED'].includes(l.status));

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
          <Link className="btn btn-ghost" to="/admin/luggage">Open / consolidate luggage</Link>
          <button className="btn btn-brand" disabled={!tripOpen || autoMut.isPending}
            onClick={() => autoMut.mutate()} data-testid="auto-assign"><Zap className="w-4 h-4" /> Auto-assign</button>
        </div>
      </div>

      <div className="card space-y-3">
        <h2 className="text-xl font-semibold">Trip capacity</h2>
        <p>{t.assignedWeightKg} / {t.totalLuggageCapacity} kg · Available {t.availableCapacityKg} kg</p>
        {(shipments.isError || luggage.isError) &&
          <p role="alert" className="text-red-600">Failed to load available packages or luggage. Refresh to try again.</p>}
        {tripOpen && <form className="space-y-2"
          onSubmit={e => { e.preventDefault(); if (selectedLuggageId) attachMut.mutate(selectedLuggageId); }}>
          <label className="label" htmlFor="attach-luggage">Add luggage to trip</label>
          <select id="attach-luggage" className="input" value={selectedLuggageId} required
            disabled={attachMut.isPending || detachMut.isPending || luggage.isLoading || luggage.isError}
            onChange={e => setSelectedLuggageId(e.target.value)}>
            <option value="">Select luggage...</option>
            {unassignedLuggage.map(l => <option key={l.id} value={l.id}
              disabled={Number(l.assignedWeightKg) > Number(t.availableCapacityKg)}>
              {l.luggageNumber} · {l.shipments.length} package(s) · {l.assignedWeightKg} kg
            </option>)}
          </select>
          <button className="btn btn-brand" data-testid="add-luggage-to-trip"
            disabled={!unassignedLuggage.some(l => l.id === selectedLuggageId
              && Number(l.assignedWeightKg) <= Number(t.availableCapacityKg))
              || attachMut.isPending || detachMut.isPending || luggage.isError}>
            {attachMut.isPending ? 'Adding...' : '+ Add luggage to trip'}
          </button>
          {!luggage.isLoading && !luggage.isError && unassignedLuggage.length === 0 &&
            <p className="text-sm text-ink-500">No unassigned luggage available. <Link className="underline" to="/admin/luggage">Open new luggage</Link> first.</p>}
        </form>}
      </div>

      <div className="card space-y-3">
        <h2 className="text-xl font-semibold">Packages assigned directly to this trip</h2>
        <ul className="divide-y divide-ink-300/50 text-sm">
          {t.directShipments.map(s => <li className="py-2 flex flex-wrap justify-between gap-2" key={s.id}>
            <span className="font-mono">{s.trackingNumber}</span>
            <span>{s.declaredWeightKg} kg · {s.destinationCity}</span>
            {tripOpen && <button className="btn btn-ghost text-xs" disabled={directMut.isPending}
              onClick={() => directMut.mutate({ shipmentId: s.id, tripId: null })}>Remove from trip</button>}
          </li>)}
          {t.directShipments.length === 0 && <li className="text-ink-500">No packages assigned directly.</li>}
        </ul>
        {tripOpen && <form className="space-y-2" onSubmit={e => {
          e.preventDefault();
          if (selectedPackageId) directMut.mutate({ shipmentId: selectedPackageId, tripId: t.id });
        }}>
          <label className="label" htmlFor="direct-package">Add package to trip (without luggage)</label>
          <select id="direct-package" className="input" value={selectedPackageId} required
            disabled={directMut.isPending || shipments.isLoading || shipments.isError}
            onChange={e => setSelectedPackageId(e.target.value)}>
            <option value="">Select an unassigned package...</option>
            {available.map(s => <option key={s.id} value={s.id}
              disabled={Number(s.declaredWeightKg) > Number(t.availableCapacityKg)}>
              {s.trackingNumber} · {s.senderName} · {s.declaredWeightKg} kg · {s.destinationCity}
            </option>)}
          </select>
          <button className="btn btn-brand" data-testid="add-package-to-trip"
            disabled={directMut.isPending || shipments.isLoading || shipments.isError
              || !available.some(s => s.id === selectedPackageId && Number(s.declaredWeightKg) <= Number(t.availableCapacityKg))}>
            {directMut.isPending ? 'Updating...' : '+ Add package to trip'}
          </button>
          {!shipments.isLoading && !shipments.isError && available.length === 0 &&
            <p className="text-sm text-ink-500">No eligible unassigned packages available.</p>}
        </form>}
      </div>

      <div className="grid md:grid-cols-2 gap-4">
        {t.luggage.map(l => (
          <div className="card space-y-3" key={l.id} data-testid={`admin-luggage-${l.luggageNumber}`}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="font-bold">{l.luggageNumber}</div>
                <div className="text-sm text-ink-500">Capacity {l.capacityKg} kg · Assigned {l.assignedWeightKg} kg · Available {l.availableCapacityKg} kg</div>
              </div>
              <div className="flex flex-wrap gap-2">
                <PrivateQr kind="luggage" value={l.id} />
                <button className="btn btn-ghost text-xs text-red-600"
                  disabled={!tripOpen || !['CREATED', 'PACKING', 'READY', 'SEALED'].includes(l.status)
                    || attachMut.isPending || detachMut.isPending}
                  title="Unassign luggage from this trip without deleting its packages"
                  data-testid={`remove-luggage-from-trip-${l.luggageNumber}`}
                  onClick={() => detachMut.mutate(l.id)}>Remove from trip</button>
              </div>
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
            <LuggagePacking luggage={l} shipments={shipments.data ?? []} tripOpen={tripOpen}
              tripAvailableKg={t.availableCapacityKg} />
          </div>
        ))}
      </div>
    </div>
  );
}
