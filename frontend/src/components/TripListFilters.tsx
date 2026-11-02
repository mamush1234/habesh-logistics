export type TripSort = 'newest' | 'oldest' | 'departure';
export interface TripFilters { from: string; to: string; sort: TripSort; origin?: string; destination?: string; verifiedOnly?: boolean }
export const initialTripFilters: TripFilters = { from: '', to: '', sort: 'newest' };

export const includesCity = (city: string, search: string) => {
  const normalize = (value: string) => value.toLowerCase().replace(/[.,]/g, '').replace(/\s+/g, ' ').trim();
  return normalize(city).includes(normalize(search));
};

export function filterTrips<T extends { id: string; departureDate: string; createdAt: string; origin: string; destination: string }>(
  trips: T[], filters: TripFilters,
): T[] {
  return trips.filter(t => (!filters.from || t.departureDate >= filters.from)
    && (!filters.to || t.departureDate <= filters.to)
    && includesCity(t.origin, filters.origin ?? '') && includesCity(t.destination, filters.destination ?? ''))
    .sort((a, b) => (filters.sort === 'departure'
      ? a.departureDate.localeCompare(b.departureDate)
      : filters.sort === 'oldest' ? Date.parse(a.createdAt) - Date.parse(b.createdAt)
        : Date.parse(b.createdAt) - Date.parse(a.createdAt)) || a.id.localeCompare(b.id));
}

export function TripListFilters({ value, onChange, showRoute = false, showVerified = false }: {
  value: TripFilters; onChange: (value: TripFilters) => void; showRoute?: boolean; showVerified?: boolean;
}) {
  return <div className="trip-list-filters">
    {showRoute && <>
      <label className="label">From<input className="input" maxLength={100} placeholder="Washington DC" value={value.origin ?? ''}
        onChange={e => onChange({ ...value, origin: e.target.value })} /></label>
      <label className="label">To<input className="input" maxLength={100} placeholder="Addis Ababa" value={value.destination ?? ''}
        onChange={e => onChange({ ...value, destination: e.target.value })} /></label>
    </>}
    <label className="label">Departure from<input className="input" type="date" value={value.from} max={value.to || undefined}
      onChange={e => onChange({ ...value, from: e.target.value })} /></label>
    <label className="label">Departure through<input className="input" type="date" value={value.to} min={value.from || undefined}
      onChange={e => onChange({ ...value, to: e.target.value })} /></label>
    <label className="label">Sort trips<select className="input" value={value.sort} onChange={e => {
      const sort = e.target.value;
      if (sort === 'newest' || sort === 'oldest' || sort === 'departure') onChange({ ...value, sort });
    }}><option value="newest">Newest posted first</option><option value="oldest">Oldest posted first</option>
      <option value="departure">Soonest departure first</option></select></label>
    <button type="button" className="btn btn-ghost" onClick={() => onChange(initialTripFilters)}>Reset trip filters</button>
    {showVerified && <label className="flex items-center gap-2 text-sm font-semibold">
      <input type="checkbox" className="accent-emerald-700" checked={value.verifiedOnly ?? false}
        onChange={e => onChange({ ...value, verifiedOnly: e.target.checked })} />Verified travelers only
    </label>}
    {value.from && value.to && value.from > value.to && <p role="alert">Departure through must be on or after Departure from.</p>}
  </div>;
}
