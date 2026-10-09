import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AgentApi, ShipmentApi, TripApi, LuggageApi, ScanApi, qrUrl } from '../../api/endpoints';
import { StatusBadge } from '../../components/StatusBadge';
import { AssignedShipments } from '../../components/ShipmentStatusSelect';
import { useState } from 'react';
import { toast } from 'sonner';

export function AgentDashboard() {
  const qc = useQueryClient();
  const stats = useQuery({ queryKey: ['agent-stats'], queryFn: AgentApi.myStats });
  const trips = useQuery({ queryKey: ['trips'], queryFn: TripApi.list });
  const referred = useQuery({ queryKey: ['agent-referrals'], queryFn: AgentApi.myReferrals });
  const assigned = useQuery({ queryKey: ['shipments'], queryFn: ShipmentApi.list });

  const [scanCode, setScanCode] = useState('');

  const scanMut = useMutation({
    mutationFn: () => ScanApi.scan(scanCode),
    onSuccess: (d) => toast.success(`Scanned ${d.type}`),
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Scan failed'),
  });

  const shipMut = useMutation({
    mutationFn: ({ id, status, desc }: { id: string; status: string; desc?: string }) =>
      ShipmentApi.updateStatus(id, status, desc, 'Addis Ababa'),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['trips'] }); toast.success('Shipment updated'); },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed'),
  });

  const lugMut = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => LuggageApi.updateStatus(id, status),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['trips'] }); toast.success('Luggage updated'); },
  });

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">Agent dashboard</h1>

      {stats.data && (
        <div className="grid md:grid-cols-4 gap-3" data-testid="agent-stats">
          <StatCard label="Referral code" value={stats.data.referralCode} />
          <StatCard label="Total referrals" value={stats.data.totalReferrals} />
          <StatCard label="Delivered" value={stats.data.deliveredShipments} />
          <StatCard label="Commission earned" value={`$${stats.data.commissionEarned}`} />
          <StatCard label="Active shipments" value={stats.data.activeShipments} />
          <StatCard label="Total kg" value={`${stats.data.totalKg} kg`} />
          <StatCard label="Commission paid" value={`$${stats.data.commissionPaid}`} />
          <StatCard label="Commission pending" value={`$${stats.data.commissionPending}`} />
        </div>
      )}

      <AssignedShipments shipments={assigned.data?.filter(s => s.assignedAgentId)} />

      <div className="card space-y-3">
        <h2 className="text-lg font-semibold">Scan a QR code</h2>
        <div className="flex gap-2">
          <input className="input" placeholder="HL-000101 or LUG-2026-0001"
            value={scanCode} onChange={e => setScanCode(e.target.value)}
            data-testid="scan-input" />
          <button className="btn btn-primary" onClick={() => scanMut.mutate()} data-testid="scan-submit">Scan</button>
        </div>
      </div>

      <div className="space-y-4">
        <h2 className="text-xl font-semibold">Incoming trips</h2>
        {trips.data?.map(t => (
          <div className="card space-y-3" key={t.id} data-testid={`agent-trip-${t.tripNumber}`}>
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="font-bold font-display text-lg">{t.tripNumber}</div>
                <div className="text-sm text-ink-500">Traveler: {t.travelerName} · {t.departureAirport} → {t.arrivalAirport} · {new Date(t.departureDate).toLocaleDateString()}</div>
              </div>
              <StatusBadge value={t.status} />
            </div>
            <div className="space-y-2">
              {t.luggage.map(l => (
                <div className="rounded-xl ring-1 ring-ink-300 p-4" key={l.id}>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="font-semibold">{l.luggageNumber}</div>
                      <div className="text-xs text-ink-500">{l.assignedWeightKg} / {l.capacityKg} kg</div>
                    </div>
                    <StatusBadge value={l.status} />
                  </div>
                  <table className="mt-3 w-full text-sm">
                    <thead className="text-xs text-ink-500">
                      <tr><th className="text-left py-1">Tracking</th><th className="text-left">Dest</th><th className="text-left">Weight</th><th className="text-left">Status</th><th className="text-right">Actions</th></tr>
                    </thead>
                    <tbody className="divide-y divide-ink-300/50">
                      {l.shipments.map(s => (
                        <tr key={s.id} data-testid={`agent-ship-${s.trackingNumber}`}>
                          <td className="py-1.5 font-mono">{s.trackingNumber}</td>
                          <td>{s.destinationCity}</td>
                          <td>{s.declaredWeightKg} kg</td>
                          <td><StatusBadge value={s.status} /></td>
                          <td className="text-right">
                            <div className="inline-flex gap-1">
                              <button className="btn btn-ghost text-[11px] !px-2 !py-1" onClick={() => shipMut.mutate({ id: s.id, status: 'RECEIVED_BY_AGENT', desc: 'Received by Ethiopia agent' })}>Receive</button>
                              <button className="btn btn-ghost text-[11px] !px-2 !py-1" onClick={() => shipMut.mutate({ id: s.id, status: 'READY_FOR_RECIPIENT', desc: 'Ready for recipient' })}>Ready</button>
                              <button className="btn btn-brand text-[11px] !px-2 !py-1" onClick={() => shipMut.mutate({ id: s.id, status: 'DELIVERED', desc: 'Delivered to recipient' })}>Deliver</button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <div className="flex gap-2 mt-3 items-center">
                    <button className="btn btn-ghost text-xs" onClick={() => lugMut.mutate({ id: l.id, status: 'RECEIVED_BY_AGENT' })}>Luggage received</button>
                    <a className="btn btn-ghost text-xs" href={qrUrl.luggage(l.id)} target="_blank" rel="noreferrer">View QR</a>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {referred.data && referred.data.length > 0 && (
        <div className="card">
          <h2 className="text-xl font-semibold mb-3">Your referrals</h2>
          <ul className="text-sm divide-y divide-ink-300/50">
            {referred.data.map(s => (
              <li className="py-2 flex items-center justify-between" key={s.id}>
                <span className="font-mono">{s.trackingNumber}</span>
                <span>{s.destinationCity} · {s.declaredWeightKg} kg</span>
                <StatusBadge value={s.status} />
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: any }) {
  return (
    <div className="card">
      <div className="text-xs uppercase tracking-widest text-ink-500">{label}</div>
      <div className="mt-1 text-xl font-bold">{value}</div>
    </div>
  );
}
