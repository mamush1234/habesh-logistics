import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { TripApi, LuggageApi, ShipmentApi } from '../../api/endpoints';
import { AssignedShipments } from '../../components/ShipmentStatusSelect';
import { StatusBadge } from '../../components/StatusBadge';
import { toast } from 'sonner';
import { PlaneTakeoff, PlaneLanding, Luggage as LuggageIcon } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';

export function TravelerDashboard() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['trips'], queryFn: TripApi.list });
  const assigned = useQuery({ queryKey: ['shipments'], queryFn: ShipmentApi.list });

  const tripMut = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => TripApi.updateStatus(id, status),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['trips'] }); qc.invalidateQueries({ queryKey: ['shipments'] }); toast.success('Trip updated'); },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed'),
  });
  const lugMut = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => LuggageApi.updateStatus(id, status),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['trips'] }); qc.invalidateQueries({ queryKey: ['shipments'] }); toast.success('Luggage updated'); },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed'),
  });

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">My trips</h1>

      <AssignedShipments shipments={assigned.data} />

      {q.isLoading && <p className="text-ink-500">Loading…</p>}
      {q.data && q.data.length === 0 && <p className="text-ink-500">No trips assigned to you yet.</p>}

      <div className="space-y-6" data-testid="traveler-trips">
        {q.data?.map(t => (
          <div className="card space-y-4" key={t.id} data-testid={`trip-${t.tripNumber}`}>
            <div className="flex items-start justify-between gap-4 flex-wrap">
              <div>
                <div className="text-xs uppercase tracking-widest text-ink-500">Trip</div>
                <div className="text-2xl font-bold font-display">{t.tripNumber}</div>
                <div className="mt-1 flex items-center gap-2 text-ink-700">
                  <PlaneTakeoff className="w-4 h-4" /> {t.departureAirport}
                  <span className="text-ink-500">→</span>
                  <PlaneLanding className="w-4 h-4" /> {t.arrivalAirport}
                  <span className="text-ink-500 ml-2">{new Date(t.departureDate).toLocaleDateString()}</span>
                </div>
              </div>
              <StatusBadge value={t.status} />
            </div>

            <div className="grid grid-cols-3 gap-3 text-sm">
              <div className="rounded-xl bg-ink-100 p-3"><div className="text-ink-500 text-xs">Capacity</div><div className="font-bold">{t.totalLuggageCapacity} kg</div></div>
              <div className="rounded-xl bg-ink-100 p-3"><div className="text-ink-500 text-xs">Assigned</div><div className="font-bold">{t.assignedWeightKg} kg</div></div>
              <div className="rounded-xl bg-ink-100 p-3"><div className="text-ink-500 text-xs">Available</div><div className="font-bold">{t.availableCapacityKg} kg</div></div>
            </div>

            <div className="space-y-3">
              {t.directShipments.length > 0 && <div className="rounded-xl ring-1 ring-ink-300 p-4">
                <h2 className="font-semibold">Direct packages (without luggage)</h2>
                <ul className="mt-3 text-sm divide-y divide-ink-300/50">
                  {t.directShipments.map(s => <li className="py-1.5 flex justify-between" key={s.id}>
                    <span className="font-mono">{s.trackingNumber}</span>
                    <span>{s.declaredWeightKg} kg · {s.destinationCity}</span>
                  </li>)}
                </ul>
              </div>}
              {t.luggage.map(l => (
                <div className="rounded-xl ring-1 ring-ink-300 p-4" key={l.id} data-testid={`luggage-${l.luggageNumber}`}>
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <LuggageIcon className="w-5 h-5 text-brand-600" />
                      <div>
                        <div className="font-semibold">{l.luggageNumber}</div>
                        <div className="text-xs text-ink-500">{l.assignedWeightKg} / {l.capacityKg} kg</div>
                      </div>
                    </div>
                    <StatusBadge value={l.status} />
                  </div>
                  <ul className="mt-3 text-sm divide-y divide-ink-300/50">
                    {l.shipments.map(s => (
                      <li className="py-1.5 flex justify-between" key={s.id}>
                        <span className="font-mono">{s.trackingNumber}</span>
                        <span>{s.declaredWeightKg} kg · {s.destinationCity}</span>
                      </li>
                    ))}
                    {l.shipments.length === 0 && <li className="py-1.5 text-ink-500">No shipments assigned.</li>}
                  </ul>
                  {(user?.role === 'ADMIN' || t.travelerId === user?.id || l.assignedTravelerId === user?.id)
                    && <div className="flex flex-wrap gap-2 mt-3">
                    <button className="btn btn-ghost text-xs" onClick={() => lugMut.mutate({ id: l.id, status: 'HANDED_TO_TRAVELER' })}>Mark received</button>
                    <button className="btn btn-ghost text-xs" onClick={() => lugMut.mutate({ id: l.id, status: 'SEALED' })}>Sealed</button>
                  </div>}
                </div>
              ))}
            </div>

            {(user?.role === 'ADMIN' || t.travelerId === user?.id) && <div className="flex flex-wrap gap-2">
              <button className="btn btn-primary" onClick={() => tripMut.mutate({ id: t.id, status: 'DEPARTED' })} data-testid={`depart-${t.tripNumber}`}>Mark departed</button>
              <button className="btn btn-brand" onClick={() => tripMut.mutate({ id: t.id, status: 'ARRIVED' })} data-testid={`arrive-${t.tripNumber}`}>Mark arrived</button>
            </div>}
          </div>
        ))}
      </div>
    </div>
  );
}
