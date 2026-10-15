import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { AdminApi, ShipmentApi, TripApi } from '../../api/endpoints';
import { ShipmentStatusSelect } from '../../components/ShipmentStatusSelect';
import { PackagePhoto } from '../../components/PackagePhoto';
import { RecipientDetails } from '../../components/RecipientDetails';
import { TravelCalendar } from '../../components/TravelCalendar';
import { TravelRequests } from '../../components/TravelRequests';

const EMPTY_TRIP = {
  travelerId: '', departureAirport: 'IAD', arrivalAirport: 'ADD',
  departureDate: '', expectedArrivalDate: '', totalLuggageCapacity: '', numberOfLuggage: '2', notes: '',
};

export function AdminDashboard() {
  const qc = useQueryClient();
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: AdminApi.dashboard });
  const trips = useQuery({ queryKey: ['trips'], queryFn: TripApi.list });
  const travelers = useQuery({ queryKey: ['admin-users', 'TRAVELER'], queryFn: () => AdminApi.users('TRAVELER') });
  const agents = useQuery({ queryKey: ['admin-users', 'AGENT'], queryFn: () => AdminApi.users('AGENT') });
  const shipments = useQuery({ queryKey: ['shipments'], queryFn: ShipmentApi.list });
  const agentCodes = useQuery({ queryKey: ['agent-codes'], queryFn: AdminApi.agentCodes });
  const agentMut = useMutation({
    mutationFn: ({ id, agentId }: { id: string; agentId: string | null }) => ShipmentApi.assignAgent(id, agentId),
    onSuccess: () => {
      toast.success('Agent updated');
      qc.invalidateQueries({ queryKey: ['shipments'] });
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed to update agent'),
  });
  const [showForm, setShowForm] = useState(false);
  const [showUser, setShowUser] = useState(false);
  const EMPTY_USER = { firstName: '', lastName: '', email: '', phone: '', password: '', role: 'TRAVELER' as 'TRAVELER' | 'AGENT' | 'SENDER' };
  const [user, setUser] = useState(EMPTY_USER);
  const updUser = (k: keyof typeof EMPTY_USER) => (e: any) => setUser({ ...user, [k]: e.target.value });
  const createUser = useMutation({
    mutationFn: () => AdminApi.createUser({ ...user, phone: user.phone || undefined }),
    onSuccess: (u) => {
      toast.success(`${u.role} ${u.firstName} ${u.lastName} created`);
      setUser(EMPTY_USER);
      setShowUser(false);
      qc.invalidateQueries({ queryKey: ['admin-users'] });
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed to create user'),
  });
  const removeMut = useMutation({
    mutationFn: (id: string) => ShipmentApi.remove(id),
    onSuccess: () => {
      toast.success('Shipment removed');
      qc.invalidateQueries({ queryKey: ['shipments'] });
      qc.invalidateQueries({ queryKey: ['trips'] });
      qc.invalidateQueries({ queryKey: ['travel-announcements'] });
      qc.invalidateQueries({ queryKey: ['travel-requests'] });
      qc.invalidateQueries({ queryKey: ['my-shipments'] });
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed to remove shipment'),
  });
  const paymentMut = useMutation({
    mutationFn: ({ id, paymentStatus }: { id: string; paymentStatus: string }) => ShipmentApi.updatePayment(id, paymentStatus),
    onSuccess: () => {
      toast.success('Payment updated');
      qc.invalidateQueries({ queryKey: ['shipments'] });
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed to update payment'),
  });
  const travelerMut = useMutation({
    mutationFn: ({ id, travelerId }: { id: string; travelerId: string | null }) => ShipmentApi.assignTraveler(id, travelerId),
    onSuccess: () => {
      toast.success('Traveler updated');
      qc.invalidateQueries({ queryKey: ['shipments'] });
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed to update traveler'),
  });
  const [trip, setTrip] = useState(EMPTY_TRIP);
  const upd = (k: keyof typeof EMPTY_TRIP) => (e: any) => setTrip({ ...trip, [k]: e.target.value });

  const createTrip = useMutation({
    mutationFn: () => TripApi.create({
      travelerId: trip.travelerId,
      departureAirport: trip.departureAirport,
      arrivalAirport: trip.arrivalAirport,
      departureDate: trip.departureDate,
      expectedArrivalDate: trip.expectedArrivalDate || undefined,
      totalLuggageCapacity: Number(trip.totalLuggageCapacity),
      numberOfLuggage: Number(trip.numberOfLuggage),
      notes: trip.notes || undefined,
    }),
    onSuccess: (t) => {
      toast.success(`Trip ${t.tripNumber} created`);
      setTrip(EMPTY_TRIP);
      setShowForm(false);
      qc.invalidateQueries({ queryKey: ['trips'] });
      qc.invalidateQueries({ queryKey: ['admin-stats'] });
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || 'Failed to create trip'),
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold">Admin dashboard</h1>
        <div className="flex gap-2">
          <Link className="btn btn-ghost" to="/admin/access">Private access</Link>
          <Link className="btn btn-ghost" to="/admin/luggage">Luggage consolidation</Link>
          <button className="btn btn-ghost" onClick={() => setShowUser(!showUser)} data-testid="admin-create-user">+ New user</button>
          <Link className="btn btn-ghost" to="/send" data-testid="admin-create-shipment">+ New shipment</Link>
          <button className="btn btn-brand" onClick={() => setShowForm(!showForm)} data-testid="admin-create-trip">+ New trip</button>
        </div>
      </div>

      <TravelCalendar allowPosting />
      <TravelRequests />

      {showUser && (
        <form className="card grid md:grid-cols-2 gap-3" data-testid="user-form"
          onSubmit={e => { e.preventDefault(); createUser.mutate(); }}>
          <div className="md:col-span-2">
            <label className="label">Role</label>
            <select className="input" value={user.role} onChange={updUser('role')}>
              <option value="TRAVELER">Traveler</option>
              <option value="AGENT">Agent</option>
              <option value="SENDER">Sender</option>
            </select>
          </div>
          <div><label className="label">First name</label><input className="input" value={user.firstName} onChange={updUser('firstName')} required /></div>
          <div><label className="label">Last name</label><input className="input" value={user.lastName} onChange={updUser('lastName')} required /></div>
          <div><label className="label">Email</label><input className="input" type="email" value={user.email} onChange={updUser('email')} required /></div>
          <div><label className="label">Phone</label><input className="input" value={user.phone} onChange={updUser('phone')} /></div>
          <div className="md:col-span-2"><label className="label">Temporary password (at least 12 characters)</label><input className="input" type="password" minLength={12} maxLength={72} value={user.password} onChange={updUser('password')} required /></div>
          <button className="btn btn-brand md:col-span-2" disabled={createUser.isPending}>
            {createUser.isPending ? 'Creating...' : 'Create user'}
          </button>
        </form>
      )}

      {showForm && (
        <form className="card grid md:grid-cols-3 gap-3" data-testid="trip-form"
          onSubmit={e => { e.preventDefault(); createTrip.mutate(); }}>
          <div className="md:col-span-3">
            <label className="label">Traveler</label>
            <select className="input" value={trip.travelerId} onChange={upd('travelerId')} required>
              <option value="">Select a traveler…</option>
              {travelers.data?.map(t => (
                <option key={t.id} value={t.id}>{t.firstName} {t.lastName} ({t.email})</option>
              ))}
            </select>
          </div>
          <div><label className="label">From (airport)</label><input className="input" value={trip.departureAirport} onChange={upd('departureAirport')} required /></div>
          <div><label className="label">To (airport)</label><input className="input" value={trip.arrivalAirport} onChange={upd('arrivalAirport')} required /></div>
          <div><label className="label">Departure date</label><input className="input" type="date" value={trip.departureDate} onChange={upd('departureDate')} required /></div>
          <div><label className="label">Expected arrival</label><input className="input" type="date" value={trip.expectedArrivalDate} onChange={upd('expectedArrivalDate')} /></div>
          <div><label className="label">Total capacity (kg)</label><input className="input" type="number" step="0.1" min="0.1" value={trip.totalLuggageCapacity} onChange={upd('totalLuggageCapacity')} required /></div>
          <div><label className="label">Pre-create luggage (0 to assign existing luggage later)</label><input className="input" type="number" min="0" value={trip.numberOfLuggage} onChange={upd('numberOfLuggage')} required /></div>
          <div className="md:col-span-3"><label className="label">Notes</label><input className="input" value={trip.notes} onChange={upd('notes')} /></div>
          <button className="btn btn-brand md:col-span-3" disabled={createTrip.isPending}>
            {createTrip.isPending ? 'Creating...' : 'Create trip'}
          </button>
        </form>
      )}

      <div className="grid md:grid-cols-4 gap-3" data-testid="admin-stats">
        {stats.data && [
          ['Total shipments', stats.data.totalShipments],
          ['Awaiting pickup', stats.data.awaitingPickup],
          ['At Alexandria', stats.data.atAlexandria],
          ['Ready to consolidate', stats.data.readyForConsolidation],
          ['Assigned to luggage', stats.data.assignedToLuggage],
          ['Active travelers', stats.data.activeTravelers],
          ['Active trips', stats.data.activeTrips],
          ['Luggage in transit', stats.data.luggageInTransit],
          ['Arrived in Ethiopia', stats.data.arrivedInEthiopia],
          ['Pending delivery', stats.data.pendingDeliveries],
          ['Revenue', `$${stats.data.revenue}`],
          ['Referral commissions', `$${stats.data.referralCommissions}`],
        ].map(([k, v]) => (
          <div className="card" key={k as string}>
            <div className="text-xs uppercase tracking-widest text-ink-500">{k}</div>
            <div className="mt-1 text-xl font-bold">{String(v)}</div>
          </div>
        ))}
      </div>

      <div className="card">
        <h2 className="text-xl font-semibold mb-3">Trips</h2>
        <table className="w-full text-sm">
          <thead className="text-xs text-ink-500">
            <tr><th className="text-left py-1">Trip</th><th className="text-left">Traveler</th><th className="text-left">Route</th><th className="text-left">Capacity</th><th className="text-left">Status</th><th></th></tr>
          </thead>
          <tbody className="divide-y divide-ink-300/50">
            {trips.data?.map(t => (
              <tr key={t.id}>
                <td className="py-2 font-mono">{t.tripNumber}</td>
                <td>{t.travelerName}</td>
                <td>{t.departureAirport} → {t.arrivalAirport}</td>
                <td>{t.assignedWeightKg} / {t.totalLuggageCapacity} kg</td>
                <td>{t.status}</td>
                <td className="text-right">
                  <Link className="btn btn-ghost text-xs" to={`/admin/trips/${t.id}`} data-testid={`admin-open-trip-${t.tripNumber}`}>Open</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h2 className="text-xl font-semibold mb-3">Shipments</h2>
        {agentCodes.data && agentCodes.data.length > 0 && (
          <p className="text-sm text-ink-500 mb-3" data-testid="agent-codes">
            Agent IDs: {agentCodes.data.map(a => `${a.name} — ${a.referralCode}`).join(' · ')}
          </p>
        )}
        <table className="w-full text-sm">
          <thead className="text-xs text-ink-500">
            <tr><th className="text-left py-1">Tracking</th><th className="text-left">Sender</th><th className="text-left">Recipients / description</th><th className="text-left">Type</th><th className="text-left">Photo</th><th className="text-left">Weight</th><th className="text-left">Fee</th><th className="text-left">Payment</th><th className="text-left">Status</th><th className="text-left">Trip / luggage</th><th className="text-left">Traveler</th><th className="text-left">Receiving agent</th><th></th></tr>
          </thead>
          <tbody className="divide-y divide-ink-300/50">
            {shipments.data?.map(s => (
              <tr key={s.id}>
                <td className="py-2 font-mono">{s.trackingNumber}</td>
                <td>{s.senderName}</td>
                <td><RecipientDetails name={s.recipient.name} phone={s.recipient.phone} recipients={s.recipients} description={s.description} /></td>
                <td>{s.packageType}</td>
                <td>
                  <PackagePhoto id={s.id} trackingNumber={s.trackingNumber} hasPhoto={!!s.hasPhoto} senderId={s.senderId} />
                </td>
                <td>{s.declaredWeightKg} kg</td>
                <td>${Number(s.totalAmount ?? 0).toFixed(2)}</td>
                <td>
                  <select className="input !py-1" value={s.paymentStatus}
                    onChange={e => paymentMut.mutate({ id: s.id, paymentStatus: e.target.value })}
                    data-testid={`shipment-payment-${s.trackingNumber}`}>
                    {['UNPAID', 'PENDING', 'PAID', 'REFUNDED', 'FAILED'].map(p => <option key={p} value={p}>{p}</option>)}
                  </select>
                </td>
                <td><ShipmentStatusSelect id={s.id} value={s.status} testId={`shipment-status-${s.trackingNumber}`} /></td>
                <td>
                  {s.tripId
                    ? <Link className="text-brand-600 underline" to={`/admin/trips/${s.tripId}`}>{s.tripNumber}</Link>
                    : <span className="text-ink-500">No trip</span>}
                  <div className="text-xs text-ink-500">{s.luggageNumber ?? 'No luggage'}</div>
                </td>
                <td>
                  <select className="input !py-1" value={s.assignedTravelerId ?? ''}
                    onChange={e => travelerMut.mutate({ id: s.id, travelerId: e.target.value || null })}
                    data-testid={`shipment-traveler-${s.trackingNumber}`}>
                    <option value="">Unassigned</option>
                    {travelers.data?.map(t => <option key={t.id} value={t.id}>{t.firstName} {t.lastName}</option>)}
                  </select>
                </td>
                <td>
                  <select className="input !py-1" value={s.assignedAgentId ?? ''}
                    onChange={e => agentMut.mutate({ id: s.id, agentId: e.target.value || null })}
                    data-testid={`shipment-agent-${s.trackingNumber}`}>
                    <option value="">Unassigned</option>
                    {agents.data?.map(a => <option key={a.id} value={a.id}>{a.firstName} {a.lastName}</option>)}
                  </select>
                </td>
                <td>
                  <button className="btn btn-ghost text-xs text-red-600" data-testid={`shipment-remove-${s.trackingNumber}`}
                    onClick={() => { if (confirm(`Remove ${s.trackingNumber}? This cannot be undone.`)) removeMut.mutate(s.id); }}>
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
