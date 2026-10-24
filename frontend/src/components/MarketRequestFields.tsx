import { itemTypes } from '../api/marketplace';
import type { ItemType } from '../api/marketplace';

export function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
export const initialRequestForm = () => ({
  pickupCity: '', destinationCity: '', availableFrom: today(), deadline: '', itemType: 'OTHER' as ItemType,
  description: '', weightKg: '', offeredPrice: '',
});
export function MarketRequestFields({ form, onChange }: {
  form: ReturnType<typeof initialRequestForm>; onChange: (form: ReturnType<typeof initialRequestForm>) => void;
}) {
  return <>
    <label className="label">Pickup city<input className="input" required maxLength={100} value={form.pickupCity} onChange={e => onChange({ ...form, pickupCity: e.target.value })} /></label>
    <label className="label">Destination city<input className="input" required maxLength={100} value={form.destinationCity} onChange={e => onChange({ ...form, destinationCity: e.target.value })} /></label>
    <label className="label">Available from<input className="input" type="date" required min={today()} value={form.availableFrom} onChange={e => onChange({ ...form, availableFrom: e.target.value })} /></label>
    <label className="label">Delivery deadline<input className="input" type="date" required min={form.availableFrom} value={form.deadline} onChange={e => onChange({ ...form, deadline: e.target.value })} /></label>
    <label className="label">Item type<select className="input" value={form.itemType} onChange={e => {
      const itemType = itemTypes.find(type => type === e.target.value);
      if (itemType) onChange({ ...form, itemType });
    }}>{itemTypes.map(type => <option key={type} value={type}>{type.replaceAll('_', ' ')}</option>)}</select></label>
    <label className="label">Weight (kg)<input className="input" type="number" min="0.1" max="99999999.99" step="0.01" required value={form.weightKg} onChange={e => onChange({ ...form, weightKg: e.target.value })} /></label>
    <label className="label">Offered price (USD)<input className="input" type="number" min="0.01" max="99999999.99" step="0.01" required value={form.offeredPrice} onChange={e => onChange({ ...form, offeredPrice: e.target.value })} /></label>
    <label className="label sm:col-span-2">Item description<textarea className="input" required maxLength={1000} value={form.description} onChange={e => onChange({ ...form, description: e.target.value })} /></label>
  </>;
}
