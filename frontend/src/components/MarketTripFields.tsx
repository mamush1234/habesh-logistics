import { itemTypes, type ItemType, type MarketTripInput, type TripItemPrice } from '../api/marketplace';
import { today } from './MarketRequestFields';

export const initialTripForm = () => ({
  origin: '', destination: '', departureDate: '', expectedArrivalDate: '', capacityKg: '',
  allowedItems: [] as ItemType[], blockedItems: [] as ItemType[],
  handoffLocation: '', notes: '',
  prices: {} as Partial<Record<ItemType, { price: string; unit: TripItemPrice['unit'] }>>,
});
export type TripFormState = ReturnType<typeof initialTripForm>;
export const tripFormInput = (form: TripFormState): MarketTripInput => {
  const { prices, ...details } = form;
  return { ...details, expectedArrivalDate: form.expectedArrivalDate || undefined, capacityKg: Number(form.capacityKg),
    itemPrices: form.allowedItems.map(category => ({
      category, price: Number(prices[category]?.price), unit: prices[category]?.unit ?? 'PER_KG',
    })) };
};
export function tripFormError(form: TripFormState): string | null {
  if (!form.origin.trim() || !form.destination.trim()) return 'Enter the origin and destination cities.';
  if (!form.departureDate || form.departureDate < today()) return 'Choose a departure date today or later.';
  if (form.expectedArrivalDate && form.expectedArrivalDate < form.departureDate) return 'Arrival date cannot be before departure.';
  if (!form.capacityKg || !/^\d+(\.\d{1,2})?$/.test(form.capacityKg)
    || Number(form.capacityKg) < 0.1 || Number(form.capacityKg) > 99999999.99)
    return 'Enter capacity of at least 0.1 kg, with at most two decimal places.';
  if (!form.allowedItems.length) return 'Select at least one category you will carry.';
  if (form.allowedItems.some(category => {
    const price = form.prices[category]?.price ?? '';
    return !/^\d+(\.\d{1,2})?$/.test(price) || Number(price) < 0.01 || Number(price) > 99999999.99;
  })) return 'Enter a USD price of at least $0.01, with at most two decimal places, for every accepted category.';
  if (form.handoffLocation.length > 500 || form.notes.length > 1000) return 'Handoff location is limited to 500 characters and notes to 1000.';
  return null;
}

export function MarketTripFields({ form, onChange, requireArrival = false }: {
  form: TripFormState; onChange: (form: TripFormState) => void; requireArrival?: boolean;
}) {
  function rule(type: ItemType, value: string) {
    onChange({ ...form,
      allowedItems: [...form.allowedItems.filter(t => t !== type), ...(value === 'allow' ? [type] : [])],
      blockedItems: [...form.blockedItems.filter(t => t !== type), ...(value === 'block' ? [type] : [])],
    });
  }
  return <>
    <label className="label">Origin city<input className="input" required maxLength={100} pattern=".*\S.*" value={form.origin} onChange={e => onChange({ ...form, origin: e.target.value })} /></label>
    <label className="label">Destination city<input className="input" required maxLength={100} pattern=".*\S.*" value={form.destination} onChange={e => onChange({ ...form, destination: e.target.value })} /></label>
    <label className="label">Departure date<input className="input" type="date" required min={today()} value={form.departureDate} onChange={e => onChange({ ...form, departureDate: e.target.value })} /></label>
    <label className="label">{requireArrival ? 'Arrival date' : 'Expected arrival (optional)'}<input className="input" type="date" required={requireArrival} min={form.departureDate || today()} value={form.expectedArrivalDate} onChange={e => onChange({ ...form, expectedArrivalDate: e.target.value })} /></label>
    <label className="label">Available weight (kg)<input className="input" type="number" min="0.1" max="99999999.99" step="0.01" required value={form.capacityKg} onChange={e => onChange({ ...form, capacityKg: e.target.value })} /></label>
    <fieldset className="sm:col-span-2 grid sm:grid-cols-2 gap-3"><legend className="font-semibold mb-2">Item rules</legend>
      <p className="sm:col-span-2 text-sm">Only explicitly allowed categories can match. Select at least one.</p>
      {itemTypes.map(type => <div key={type} className="space-y-2 rounded-xl border border-ink-300 p-3"><label className="label">{type.replaceAll('_', ' ')}<select className="input"
        value={form.allowedItems.includes(type) ? 'allow' : form.blockedItems.includes(type) ? 'block' : ''} onChange={e => rule(type, e.target.value)}>
        <option value="">Not accepted</option><option value="allow">Will carry</option><option value="block">Will not carry</option>
      </select></label>
        {form.allowedItems.includes(type) && <>
          <label className="label">{type.replaceAll('_', ' ')} price (USD)<input className="input" type="number" required min="0.01" max="99999999.99" step="0.01"
            value={form.prices[type]?.price ?? ''} onChange={e => onChange({ ...form, prices: { ...form.prices,
              [type]: { price: e.target.value, unit: form.prices[type]?.unit ?? 'PER_KG' } } })} /></label>
          <label className="label">{type.replaceAll('_', ' ')} price unit<select className="input" value={form.prices[type]?.unit ?? 'PER_KG'}
            onChange={e => onChange({ ...form, prices: { ...form.prices,
              [type]: { price: form.prices[type]?.price ?? '', unit: e.target.value === 'PER_ITEM' ? 'PER_ITEM' : 'PER_KG' } } })}>
            <option value="PER_KG">Per kg</option><option value="PER_ITEM">Per item</option>
          </select></label>
        </>}
      </div>)}
    </fieldset>
    <label className="label sm:col-span-2">Preferred handoff location (optional)<input className="input" maxLength={500}
      value={form.handoffLocation} onChange={e => onChange({ ...form, handoffLocation: e.target.value })} /></label>
    <label className="label sm:col-span-2">Trip notes (optional)<textarea className="input" maxLength={1000}
      value={form.notes} onChange={e => onChange({ ...form, notes: e.target.value })} /></label>
    <p className="text-sm text-ink-500 sm:col-span-2">Prices are in USD and do not include platform fees. A per-item price applies to each listed package item.
      Do not include phone numbers, emails, or private addresses in handoff details or notes; these can be seen by signed-in users viewing this trip.</p>
  </>;
}

export function TripDetailsSummary({ trip }: { trip: Pick<MarketTripInput, 'itemPrices' | 'handoffLocation' | 'notes'> }) {
  return <div className="space-y-2 text-sm">
    {trip.itemPrices && trip.itemPrices.length > 0
      ? <ul>{trip.itemPrices.map(p => <li key={p.category}>{p.category.replaceAll('_', ' ')}: ${p.price.toFixed(2)} {p.unit === 'PER_KG' ? 'per kg' : 'per item'}</li>)}</ul>
      : <p className="text-ink-500">Category prices not provided.</p>}
    {trip.handoffLocation && <p className="break-words">Preferred handoff: {trip.handoffLocation}</p>}
    {trip.notes && <p className="whitespace-pre-wrap break-words">Notes: {trip.notes}</p>}
  </div>;
}
