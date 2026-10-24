import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { GuestRequestsApi } from '../api/guestRequests';
import type { GuestConversionInput } from '../api/guestRequests';
import type { MarketRequest } from '../api/marketplace';
import { marketError } from '../pages/Marketplace';
import { today } from './MarketRequestFields';

export function GuestShipmentConversion({ request }: { request: MarketRequest }) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<GuestConversionInput>({
    destinationCountry: '', recipient: { name: '', phone: '', city: request.destinationCity, address: '' },
    pickupMethod: 'ALEXANDRIA_DROPOFF', pickupAddress: '', pickupRequestedDate: '', pickupTimeWindow: '',
  });
  const conversion = useMutation({
    mutationFn: () => GuestRequestsApi.convert(request.id, {
      ...form, pickupAddress: form.pickupMethod === 'PICKUP' ? form.pickupAddress : undefined,
      pickupRequestedDate: form.pickupMethod === 'PICKUP' ? form.pickupRequestedDate || undefined : undefined,
      pickupTimeWindow: form.pickupMethod === 'PICKUP' ? form.pickupTimeWindow || undefined : undefined,
    }),
    onSuccess: shipment => {
      qc.invalidateQueries({ queryKey: ['marketplace'] });
      qc.invalidateQueries({ queryKey: ['admin-guest-links'] });
      for (const key of ['shipments', 'admin-stats', 'public-travel']) qc.invalidateQueries({ queryKey: [key] });
      toast.success(`Converted to shipment ${shipment.trackingNumber}`);
      setOpen(false);
    },
    onError: error => {
      qc.invalidateQueries({ queryKey: ['marketplace'] });
      marketError(error);
    },
  });
  const shipmentId = conversion.data?.id ?? request.convertedShipmentId;
  const tracking = conversion.data?.trackingNumber ?? request.shipmentTrackingNumber;
  if (shipmentId) return <section className="card space-y-3" role="status">
    <h2 className="text-xl font-bold">Converted to shipment {tracking}</h2>
    <p>Marketplace offers are closed. Continue with payment, receiving agent, traveler assignment, luggage and trip processing in the regular shipment workflow. Guest contact details remain on this request for admin use.</p>
    <div className="flex flex-wrap gap-2">
      <Link className="btn btn-brand" to={`/admin#shipment-${shipmentId}`}>Manage shipment</Link>
      <Link className="btn btn-ghost" to={`/track/${tracking}`}>View tracking</Link>
    </div>
  </section>;
  if (!['REQUESTED', 'MATCHED', 'PAID_ESCROW'].includes(request.status)) return null;
  return <section className="card space-y-4">
    <h2 className="text-xl font-bold">Convert guest request to shipment</h2>
    <p>Create a tracking number and follow the regular shipment workflow. The package description, destination city, weight and product photo are carried over. No sender account is created.</p>
    <p className="text-sm">Normal shipment fees apply, not the guest's offered marketplace price. Conversion closes marketplace offers, releases any reserved trip weight and refunds any simulated escrow. A selected traveler or agent becomes the assigned shipment carrier, but luggage and operational trips must still be assigned separately.</p>
    {!open ? <button className="btn btn-brand" onClick={() => setOpen(true)}>Convert to shipment</button>
      : <form className="grid sm:grid-cols-2 gap-4" onSubmit={e => { e.preventDefault(); conversion.mutate(); }}>
        <fieldset className="contents" disabled={conversion.isPending}>
          <label className="label sm:col-span-2">Destination country<input className="input" required maxLength={100} value={form.destinationCountry} onChange={e => setForm({ ...form, destinationCountry: e.target.value })} /></label>
          <label className="label">Recipient name<input className="input" required value={form.recipient.name} onChange={e => setForm({ ...form, recipient: { ...form.recipient, name: e.target.value } })} /></label>
          <label className="label">Recipient phone<input className="input" type="tel" required value={form.recipient.phone} onChange={e => setForm({ ...form, recipient: { ...form.recipient, phone: e.target.value } })} /></label>
          <label className="label">Recipient city<input className="input" required value={form.recipient.city} onChange={e => setForm({ ...form, recipient: { ...form.recipient, city: e.target.value } })} /></label>
          <label className="label">Recipient address<input className="input" value={form.recipient.address} onChange={e => setForm({ ...form, recipient: { ...form.recipient, address: e.target.value } })} /></label>
          <label className="label sm:col-span-2">Delivery method<select className="input" value={form.pickupMethod} onChange={e => setForm({ ...form, pickupMethod: e.target.value === 'PICKUP' ? 'PICKUP' : 'ALEXANDRIA_DROPOFF' })}>
            <option value="ALEXANDRIA_DROPOFF">Drop off at Alexandria</option><option value="PICKUP">Schedule pickup</option>
          </select></label>
          {form.pickupMethod === 'PICKUP' && <>
            <label className="label sm:col-span-2">Pickup address<input className="input" required maxLength={500} value={form.pickupAddress} onChange={e => setForm({ ...form, pickupAddress: e.target.value })} /></label>
            <label className="label">Pickup date<input className="input" type="date" min={today()} required value={form.pickupRequestedDate} onChange={e => setForm({ ...form, pickupRequestedDate: e.target.value })} /></label>
            <label className="label">Pickup time window<input className="input" maxLength={100} value={form.pickupTimeWindow} onChange={e => setForm({ ...form, pickupTimeWindow: e.target.value })} /></label>
          </>}
          <div className="flex flex-wrap gap-2 sm:col-span-2">
            <button className="btn btn-brand" disabled={conversion.isPending}>{conversion.isPending ? 'Converting...' : 'Confirm conversion and create shipment'}</button>
            <button className="btn btn-ghost" type="button" onClick={() => setOpen(false)}>Cancel</button>
          </div>
        </fieldset>
      </form>}
  </section>;
}
