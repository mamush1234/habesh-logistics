import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { LuggageApi, ShipmentApi, TripApi } from '../../api/endpoints';
import { PrivateQr } from '../../components/PrivateQr';
import { assignmentError, LuggagePacking } from '../../components/LuggagePacking';

const EMPTY_LUGGAGE = { capacityKg: '', luggageType: '', sealNumber: '', notes: '' };

export function AdminLuggage() {
  const qc = useQueryClient();
  const luggage = useQuery({ queryKey: ['luggage'], queryFn: LuggageApi.list });
  const shipments = useQuery({ queryKey: ['shipments'], queryFn: ShipmentApi.list });
  const trips = useQuery({ queryKey: ['trips'], queryFn: TripApi.list });
  const [form, setForm] = useState(EMPTY_LUGGAGE);
  const [selectedLuggageId, setSelectedLuggageId] = useState('');
  const refresh = () => {
    for (const key of ['luggage', 'trip', 'trips', 'shipments', 'admin-stats']) {
      qc.invalidateQueries({ queryKey: [key] });
    }
  };
  const create = useMutation({
    mutationFn: () => LuggageApi.create({
      capacityKg: Number(form.capacityKg), luggageType: form.luggageType || undefined,
      sealNumber: form.sealNumber || undefined, notes: form.notes || undefined,
    }),
    onSuccess: (created) => {
      setForm(EMPTY_LUGGAGE);
      setSelectedLuggageId(created.id);
      refresh();
      toast.success('Luggage opened for consolidation');
    },
    onError: (error: unknown) => toast.error(assignmentError(error, 'Failed to create luggage')),
  });
  const assignTrip = useMutation({
    mutationFn: ({ id, tripId }: { id: string; tripId: string | null }) => LuggageApi.assignTrip(id, tripId),
    onSuccess: () => { refresh(); toast.success('Luggage trip updated'); },
    onError: (error: unknown) => toast.error(assignmentError(error, 'Failed to update luggage trip')),
  });
  const openTrips = (trips.data ?? []).filter(t => ['OPEN', 'FILLING', 'READY'].includes(t.status));
  const selectedLuggage = luggage.data?.find(l => l.id === selectedLuggageId) ?? luggage.data?.[0];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-3">
        <h1 className="text-3xl font-bold">Luggage consolidation</h1>
        <Link className="btn btn-ghost" to="/admin">Back to dashboard</Link>
      </div>
      <p className="text-ink-500">Open luggage, consolidate packages, then assign the luggage to a trip. Direct package assignment remains available on each trip.</p>
      <form className="card grid md:grid-cols-2 gap-3" data-testid="luggage-form"
        onSubmit={e => { e.preventDefault(); create.mutate(); }}>
        <div><label className="label" htmlFor="luggage-capacity">Capacity (kg)</label>
          <input id="luggage-capacity" className="input" type="number" min="0.1" step="0.01" required
            value={form.capacityKg} onChange={e => setForm({ ...form, capacityKg: e.target.value })} /></div>
        <div><label className="label" htmlFor="luggage-type">Luggage type</label>
          <input id="luggage-type" className="input" maxLength={64} value={form.luggageType}
            onChange={e => setForm({ ...form, luggageType: e.target.value })} /></div>
        <div><label className="label" htmlFor="luggage-seal">Seal number (optional)</label>
          <input id="luggage-seal" className="input" maxLength={64} value={form.sealNumber}
            onChange={e => setForm({ ...form, sealNumber: e.target.value })} /></div>
        <div><label className="label" htmlFor="luggage-notes">Notes</label>
          <input id="luggage-notes" className="input" maxLength={1000} value={form.notes}
            onChange={e => setForm({ ...form, notes: e.target.value })} /></div>
        <button className="btn btn-brand md:col-span-2" disabled={create.isPending}>
          {create.isPending ? 'Opening...' : '+ Open new luggage'}
        </button>
      </form>
      {luggage.isLoading && <p>Loading luggage...</p>}
      {(luggage.isError || shipments.isError || trips.isError) &&
        <p role="alert" className="text-red-600">Failed to load consolidation data. Refresh to try again.</p>}
      {luggage.data?.length === 0 && <p className="text-ink-500">No luggage yet. Open your first luggage above.</p>}
      {selectedLuggage && <div>
        <label className="label" htmlFor="selected-luggage">Choose luggage to consolidate</label>
        <select id="selected-luggage" className="input" value={selectedLuggage.id}
          disabled={assignTrip.isPending} onChange={e => setSelectedLuggageId(e.target.value)}>
          {luggage.data?.map(l => <option key={l.id} value={l.id}>
            {l.luggageNumber} · {l.assignedWeightKg} / {l.capacityKg} kg · {l.tripNumber ?? 'No trip'} · {l.status}
          </option>)}
        </select>
      </div>}
      <div>
        {(selectedLuggage ? [selectedLuggage] : []).map(l => {
          const currentTrip = trips.data?.find(t => t.id === l.tripId);
          const tripOpen = !l.tripId || (!!currentTrip && openTrips.some(t => t.id === l.tripId));
          const canAssign = tripOpen && ['CREATED', 'PACKING', 'READY', 'SEALED'].includes(l.status);
          return (
            <div className="card space-y-3" key={l.id} data-testid={`admin-luggage-${l.luggageNumber}`}>
              <div className="flex justify-between gap-2">
                <div><h2 className="font-bold">{l.luggageNumber}</h2>
                  <p className="text-sm text-ink-500">{l.assignedWeightKg} / {l.capacityKg} kg · Available {l.availableCapacityKg} kg</p></div>
                <PrivateQr kind="luggage" value={l.id} />
              </div>
              {l.tripId ? <div className="flex flex-wrap justify-between items-center gap-2">
                <Link className="text-brand-600 underline" to={`/admin/trips/${l.tripId}`}>{l.tripNumber}</Link>
                {canAssign && <button className="btn btn-ghost text-xs" disabled={assignTrip.isPending}
                  onClick={() => assignTrip.mutate({ id: l.id, tripId: null })}>Remove from trip</button>}
              </div> : <div>
                <label className="label" htmlFor={`trip-${l.id}`}>Assign consolidated luggage to a trip</label>
                <select id={`trip-${l.id}`} className="input" value="" disabled={!canAssign || assignTrip.isPending || trips.isLoading}
                  onChange={e => { if (e.target.value) assignTrip.mutate({ id: l.id, tripId: e.target.value }); }}>
                  <option value="">Not assigned - select a trip</option>
                  {openTrips.map(t => <option key={t.id} value={t.id}
                    disabled={Number(l.assignedWeightKg) > Number(t.availableCapacityKg)}>
                    {t.tripNumber} · {t.travelerName} · {t.availableCapacityKg} kg available
                  </option>)}
                </select>
              </div>}
              <LuggagePacking key={l.id} luggage={l} shipments={shipments.data ?? []} tripOpen={tripOpen}
                twoColumns tripAvailableKg={currentTrip?.availableCapacityKg} />
            </div>
          );
        })}
      </div>
    </div>
  );
}
