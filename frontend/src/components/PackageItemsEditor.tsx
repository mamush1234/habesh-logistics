import { type ItemType, type PackageItemInput, type PackageReceiver } from '../api/marketplace';

export const packageCategories: { value: ItemType; label: string }[] = [
  { value: 'CLOTHING', label: 'Clothing' }, { value: 'SHOES', label: 'Shoes' },
  { value: 'LAPTOP', label: 'Laptop' }, { value: 'ELECTRONICS', label: 'Electronics' },
  { value: 'COSMETICS', label: 'Cosmetics' }, { value: 'OTHER', label: 'Others' },
  { value: 'DOCUMENTS', label: 'Documents' }, { value: 'MEDICINE', label: 'Medicine' }, { value: 'FOOD', label: 'Food' },
];
export interface PackageContents {
  items: { key: string; category: ItemType; weightKg: string; description: string }[];
  receiver: PackageReceiver;
}
const newItem = () => ({ key: crypto.randomUUID(), category: 'OTHER' as ItemType, weightKg: '', description: '' });
export const initialPackageContents = (): PackageContents => ({
  items: [newItem()], receiver: { name: '', phone: '', address: '', notes: '' },
});
export const totalPackageWeight = (contents: PackageContents) =>
  contents.items.reduce((sum, item) => sum + Math.round(Number(item.weightKg || 0) * 100), 0) / 100;
export function packageContentsError(contents: PackageContents): string | null {
  if (!contents.items.length || contents.items.length > 50) return 'Add between 1 and 50 items.';
  if (contents.items.some(item => !item.description.trim() || item.description.length > 1000 || !item.weightKg
    || !/^\d+(\.\d{1,2})?$/.test(item.weightKg) || Number(item.weightKg) < 0.1 || Number(item.weightKg) > 99999999.99))
    return 'Each item needs a description and a weight of at least 0.1 kg, with at most two decimal places.';
  if (totalPackageWeight(contents) > 99999999.99) return 'Total weight must not exceed 99999999.99 kg.';
  if (!contents.receiver.name.trim() || !contents.receiver.address.trim()) return 'Enter the receiver name and address in Ethiopia.';
  if (contents.receiver.name.length > 255 || contents.receiver.address.length > 500 || (contents.receiver.notes?.length ?? 0) > 1000)
    return 'Receiver name must be at most 255 characters, address 500, and notes 1000.';
  if (!/^(?=(?:\D*\d){7})\+?[0-9 ()-]{7,40}$/.test(contents.receiver.phone)) return 'Enter a valid receiver phone number with at least seven digits.';
  return null;
}
export const packageContentsInput = (contents: PackageContents): { items: PackageItemInput[]; receiver: PackageReceiver } => ({
  items: contents.items.map(item => ({ category: item.category, weightKg: Number(item.weightKg), description: item.description.trim() })),
  receiver: contents.receiver,
});

export function PackageItemsEditor({ value, onChange }: {
  value: PackageContents; onChange: (contents: PackageContents) => void;
}) {
  const update = (index: number, patch: Partial<PackageContents['items'][number]>) =>
    onChange({ ...value, items: value.items.map((item, i) => i === index ? { ...item, ...patch } : item) });
  return <div className="space-y-5 sm:col-span-2">
    <h2 className="text-lg font-bold">Package items</h2>
    {value.items.map((item, index) => <fieldset key={item.key} className="rounded-xl border border-ink-300 p-4 space-y-3">
      <legend className="px-1 font-semibold">Item {index + 1}</legend>
      <div className="flex flex-wrap gap-2" aria-label={`Item ${index + 1} category`}>
        {packageCategories.map(category => <button type="button" key={category.value} aria-pressed={item.category === category.value}
          className={`btn ${item.category === category.value ? 'bg-emerald-700 text-white' : 'btn-ghost'}`}
          onClick={() => update(index, { category: category.value })}>{category.label}</button>)}
      </div>
      <label className="label">Item {index + 1} weight (kg)<input className="input" type="number" required min="0.1" max="99999999.99" step="0.01"
        value={item.weightKg} onChange={e => update(index, { weightKg: e.target.value })} /></label>
      <label className="label">Item {index + 1} description<textarea className="input" required maxLength={1000} value={item.description}
        onChange={e => update(index, { description: e.target.value })} /></label>
      <button type="button" className="btn btn-ghost text-rose-700" disabled={value.items.length === 1}
        aria-label={`Remove item ${index + 1}`} onClick={() => onChange({ ...value, items: value.items.filter((_, i) => i !== index) })}>Remove item</button>
    </fieldset>)}
    <button type="button" className="btn btn-ghost" disabled={value.items.length >= 50}
      onClick={() => onChange({ ...value, items: [...value.items, newItem()] })}>Add Another Item</button>
    <p className="font-semibold text-emerald-800" role="status">Total weight: {totalPackageWeight(value).toFixed(2)} kg</p>
    <fieldset className="space-y-3">
      <legend className="text-lg font-bold mb-3">Receiver in Ethiopia</legend>
      {(['name', 'phone', 'address', 'notes'] as const).map(field => <label className="label" key={field}>
        {{ name: 'Receiver name', phone: 'Receiver phone', address: 'Receiver address in Ethiopia', notes: 'Receiver notes (optional)' }[field]}
        {field === 'address' || field === 'notes' ? <textarea className="input" required={field === 'address'} maxLength={field === 'address' ? 500 : 1000}
          value={value.receiver[field] ?? ''} onChange={e => onChange({ ...value, receiver: { ...value.receiver, [field]: e.target.value } })} />
          : <input className="input" required type={field === 'phone' ? 'tel' : 'text'} maxLength={field === 'phone' ? 40 : 255}
            value={value.receiver[field]} onChange={e => onChange({ ...value, receiver: { ...value.receiver, [field]: e.target.value } })} />}
      </label>)}
      <p className="text-sm text-ink-500">Receiver contact details are private, not shown on public package previews.</p>
    </fieldset>
  </div>;
}

export function PackageContentsSummary({ items, receiver }: { items: PackageItemInput[]; receiver?: PackageReceiver | null }) {
  return <div className="space-y-3">
    <ul className="space-y-2">{items.map((item, index) => <li key={index} className="rounded-xl bg-ink-100 p-3">
      <p className="font-semibold">{packageCategories.find(c => c.value === item.category)?.label ?? item.category} &middot; {item.weightKg} kg</p>
      <p className="whitespace-pre-wrap break-words">{item.description}</p>
    </li>)}</ul>
    {receiver && <div className="space-y-1">
      <h3 className="font-bold">Receiver in Ethiopia</h3><p>{receiver.name} &middot; {receiver.phone}</p>
      <p className="whitespace-pre-wrap break-words">{receiver.address}</p>
      {receiver.notes && <p className="whitespace-pre-wrap break-words">{receiver.notes}</p>}
    </div>}
  </div>;
}
