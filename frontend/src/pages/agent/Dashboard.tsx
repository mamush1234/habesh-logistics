import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AgentApi, ShipmentApi, TripApi, LuggageApi, ScanApi } from '../../api/endpoints';
import { PrivateQr } from '../../components/PrivateQr';
import { StatusBadge } from '../../components/StatusBadge';
import { AssignedShipments } from '../../components/ShipmentStatusSelect';
import { useState } from 'react';
import { toast } from 'sonner';
import { RecipientDetails } from '../../components/RecipientDetails';
import { PackagePhoto } from '../../components/PackagePhoto';
import { useAuth } from '../../auth/AuthContext';
import { TravelCalendar } from '../../components/TravelCalendar';
import { TravelRequests } from '../../components/TravelRequests';
import { Link } from 'react-router-dom';

export function AgentDashboard() {
  const { user } = useAuth();
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
      <div className="card space-y-3">
        <h2 className="text-xl font-semibold">Package offers and chat</h2>
        <p>Post a delivery trip and send offers for matching sender requests. Once your offer is accepted, open the package to chat with its sender.</p>
        <div className="flex flex-wrap gap-2">
          <Link to="/traveler" className="btn btn-brand">My delivery trips</Link>
          <Link to="/marketplace" className="btn btn-ghost">Sender requests and deliveries</Link>
        </div>
      </div>

      <TravelCalendar allowPosting />
      <TravelRequests />

      {stats.data && (
        <div className="grid md:grid-cols-4 gap-3" data-testid="agent-stats">
          <StatCard label="Referral code" value={stats.data.referralCode} />
          <StatCard label="Total referrals" value={stats.data.totalReferrals} />
          <StatCard label="Delivered" value={stats.data.deliveredShipments} />
          <StatCard label="Active shipments" value={stats.data.activeShipments} />
          <StatCard label="Total kg" value={`${stats.data.totalKg} kg`} />
        </div>
      )}

      <AssignedShipments shipments={assigned.data?.filter(s => s.assignedAgentId === user?.id
        || trips.data?.some(t => t.luggage.some(l => l.assignedAgentId === user?.id && l.shipments.some(p => p.id === s.id))))}
        showRecipientDetails />

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
        <p className="text-sm text-ink-500">Only your authorized packages are shown. Capacity totals include all packages on the trip.</p>
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
              {t.directShipments.length > 0 && <div className="rounded-xl ring-1 ring-ink-300 p-4">
                <h3 className="font-semibold">Direct packages</h3>
                <ul className="divide-y divide-ink-300/50">
                  {t.directShipments.map(s => <li className="py-3 space-y-2" key={s.id}>
                    <div className="flex flex-wrap justify-between gap-2">
                      <span className="font-mono">{s.trackingNumber}</span>
                      <span>{s.destinationCity} · {s.declaredWeightKg} kg</span>
                      <StatusBadge value={s.status} />
                    </div>
                    <RecipientDetails name={s.recipientName} phone={s.recipientPhone} recipients={s.recipients} description={s.description} />
                    {assigned.data?.some(item => item.id === s.id) &&
                      <PackagePhoto id={s.id} trackingNumber={s.trackingNumber} hasPhoto={s.hasPhoto} />}
                  </li>)}
                </ul>
              </div>}
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
                      <tr><th className="text-left py-1">Tracking</th><th className="text-left">Picture</th><th className="text-left">Recipient / description</th><th className="text-left">Dest</th><th className="text-left">Weight</th><th className="text-left">Status</th><th className="text-right">Actions</th></tr>
                    </thead>
                    <tbody className="divide-y divide-ink-300/50">
                      {l.shipments.map(s => (
                        <tr key={s.id} data-testid={`agent-ship-${s.trackingNumber}`}>
                          <td className="py-1.5 font-mono">{s.trackingNumber}</td>
                          <td className="py-2">
                            {l.assignedAgentId === user?.id || assigned.data?.some(item => item.id === s.id)
                              ? <PackagePhoto id={s.id} trackingNumber={s.trackingNumber} hasPhoto={s.hasPhoto} />
                              : <span className="text-xs text-ink-500">Picture restricted to assigned agent</span>}
                          </td>
                          <td className="py-2"><RecipientDetails name={s.recipientName} phone={s.recipientPhone} recipients={s.recipients} description={s.description} /></td>
                          <td>{s.destinationCity}</td>
                          <td>{s.declaredWeightKg} kg</td>
                          <td><StatusBadge value={s.status} /></td>
                          <td className="text-right">
                            {(l.assignedAgentId === user?.id
                              || assigned.data?.some(item => item.id === s.id && item.assignedAgentId === user?.id)) && <div className="inline-flex gap-1">
                              <button className="btn btn-ghost text-[11px] !px-2 !py-1" onClick={() => shipMut.mutate({ id: s.id, status: 'RECEIVED_BY_AGENT', desc: 'Received by Ethiopia agent' })}>Receive</button>
                              <button className="btn btn-ghost text-[11px] !px-2 !py-1" onClick={() => shipMut.mutate({ id: s.id, status: 'READY_FOR_RECIPIENT', desc: 'Ready for recipient' })}>Ready</button>
                              <button className="btn btn-brand text-[11px] !px-2 !py-1" onClick={() => shipMut.mutate({ id: s.id, status: 'DELIVERED', desc: 'Delivered to recipient' })}>Deliver</button>
                            </div>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <div className="flex gap-2 mt-3 items-center">
                    {l.assignedAgentId === user?.id && <button className="btn btn-ghost text-xs"
                      onClick={() => lugMut.mutate({ id: l.id, status: 'RECEIVED_BY_AGENT' })}>Luggage received</button>}
                    {l.assignedAgentId === user?.id && <PrivateQr kind="luggage" value={l.id} />}
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
              <li className="py-2 flex flex-wrap items-center justify-between gap-3" key={s.id}>
                <span className="font-mono">{s.trackingNumber}</span>
                <RecipientDetails name={s.recipient.name} phone={s.recipient.phone} recipients={s.recipients} description={s.description} />
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
