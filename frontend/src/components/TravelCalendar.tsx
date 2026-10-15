import { useId, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { TravelAnnouncementApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { Link } from 'react-router-dom';

const EMPTY_FORM = { origin: '', destination: '', departureDate: '', expectedArrivalDate: '', notes: '', totalCapacityKg: '', numberOfLuggage: '' };

function dateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function displayDate(value: string) {
  return new Date(`${value}T00:00:00`).toLocaleDateString();
}

export function TravelCalendar({ allowPosting = false }: { allowPosting?: boolean }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const id = useId();
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [capacity, setCapacity] = useState({ totalCapacityKg: '', numberOfLuggage: '' });
  const canPost = allowPosting && (user?.role === 'AGENT' || user?.role === 'ADMIN');
  const q = useQuery({
    queryKey: ['travel-announcements'],
    queryFn: TravelAnnouncementApi.list,
    refetchInterval: 60_000,
  });
  const create = useMutation({
    mutationFn: () => TravelAnnouncementApi.create({
      origin: form.origin.trim(),
      destination: form.destination.trim(),
      departureDate: form.departureDate,
      expectedArrivalDate: form.expectedArrivalDate || undefined,
      notes: form.notes.trim() || undefined,
      totalCapacityKg: Number(form.totalCapacityKg),
      numberOfLuggage: form.numberOfLuggage === '' ? undefined : Number(form.numberOfLuggage),
    }),
    onSuccess: (announcement) => {
      qc.invalidateQueries({ queryKey: ['travel-announcements'] });
      const date = new Date(`${announcement.departureDate}T00:00:00`);
      setMonth(new Date(date.getFullYear(), date.getMonth(), 1));
      setSelectedDate(announcement.departureDate);
      setForm(EMPTY_FORM);
      setShowForm(false);
      toast.success('Travel announcement published');
    },
    onError: (error: unknown) => toast.error(
      isAxiosError<{ message?: string }>(error)
        ? error.response?.data?.message || 'Failed to publish travel announcement'
        : 'Failed to publish travel announcement'),
  });
  const updateCapacity = useMutation({
    mutationFn: (announcementId: string) => TravelAnnouncementApi.updateCapacity(announcementId, {
      totalCapacityKg: Number(capacity.totalCapacityKg),
      numberOfLuggage: capacity.numberOfLuggage === '' ? undefined : Number(capacity.numberOfLuggage),
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['travel-announcements'] });
      setEditingId(null);
      toast.success('Travel capacity updated');
    },
    onError: (error: unknown) => toast.error(isAxiosError<{ message?: string }>(error)
      ? error.response?.data?.message || 'Could not update capacity' : 'Could not update capacity'),
  });
  const monthKey = dateKey(month).slice(0, 7);
  const monthAnnouncements = q.data?.filter(a => a.departureDate.startsWith(monthKey)) ?? [];
  const visible = selectedDate
    ? monthAnnouncements.filter(a => a.departureDate === selectedDate)
    : monthAnnouncements;
  const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const today = dateKey(new Date());

  function changeMonth(offset: number) {
    setMonth(new Date(month.getFullYear(), month.getMonth() + offset, 1));
    setSelectedDate(null);
  }

  return (
    <section className="card space-y-4" aria-label="Travel announcements" data-testid="travel-calendar">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold">Travel calendar</h2>
          <p className="text-sm text-ink-500">Choose travel and request space. Kilograms are reserved only after agent approval.</p>
        </div>
        {canPost && <button type="button" className="btn btn-brand" onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Cancel' : '+ Post travel'}
        </button>}
      </div>

      {canPost && showForm && (
        <form className="grid md:grid-cols-2 gap-3 rounded-xl bg-ink-100 p-4"
          onSubmit={e => { e.preventDefault(); create.mutate(); }} data-testid="travel-announcement-form">
          <p className="md:col-span-2 text-sm text-ink-500">No traveler assignment needed. All details below will be visible to signed-in senders.</p>
          <div>
            <label className="label" htmlFor={`${id}-origin`}>From (city or airport)</label>
            <input id={`${id}-origin`} className="input" required maxLength={100} value={form.origin}
              onChange={e => setForm({ ...form, origin: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor={`${id}-destination`}>To (city or airport)</label>
            <input id={`${id}-destination`} className="input" required maxLength={100} value={form.destination}
              onChange={e => setForm({ ...form, destination: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor={`${id}-departure`}>Departure date</label>
            <input id={`${id}-departure`} className="input" type="date" required min={today} value={form.departureDate}
              onChange={e => setForm({ ...form, departureDate: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor={`${id}-arrival`}>Expected arrival date (optional)</label>
            <input id={`${id}-arrival`} className="input" type="date" min={form.departureDate || today}
              value={form.expectedArrivalDate} onChange={e => setForm({ ...form, expectedArrivalDate: e.target.value })} />
          </div>
          <div className="md:col-span-2">
            <label className="label" htmlFor={`${id}-notes`}>Announcement details (optional, visible to senders)</label>
            <textarea id={`${id}-notes`} className="input" maxLength={1000} value={form.notes}
              onChange={e => setForm({ ...form, notes: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor={`${id}-capacity`}>Total available capacity (kg)</label>
            <input id={`${id}-capacity`} className="input" type="number" required min="0.1" max="99999999.99" step="0.01"
              value={form.totalCapacityKg} onChange={e => setForm({ ...form, totalCapacityKg: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor={`${id}-luggage`}>Luggage count (optional, information only)</label>
            <input id={`${id}-luggage`} className="input" type="number" min="0" max="2147483647" step="1"
              value={form.numberOfLuggage} onChange={e => setForm({ ...form, numberOfLuggage: e.target.value })} />
          </div>
          <button className="btn btn-brand md:col-span-2" disabled={create.isPending}>
            {create.isPending ? 'Publishing...' : 'Publish travel announcement'}
          </button>
        </form>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2">
        <button type="button" className="btn btn-ghost" aria-label="Previous month" onClick={() => changeMonth(-1)}>Previous</button>
        <h3 className="font-semibold" aria-live="polite">{month.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</h3>
        <button type="button" className="btn btn-ghost" aria-label="Next month" onClick={() => changeMonth(1)}>Next</button>
      </div>
      {q.isPending && <p className="text-ink-500" role="status">Loading travel announcements...</p>}
      {q.isError && <div role="alert" className="text-red-700">
        Could not load travel announcements. <button type="button" className="underline" onClick={() => q.refetch()}>Retry</button>
      </div>}
      <div className="grid grid-cols-7 gap-1 text-center">
        {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(day => (
          <div className="text-xs text-ink-500 py-2" key={day}>{day}</div>
        ))}
        {Array.from({ length: month.getDay() }, (_, i) => <div key={`blank-${i}`} />)}
        {Array.from({ length: days }, (_, i) => {
          const key = `${monthKey}-${String(i + 1).padStart(2, '0')}`;
          const count = monthAnnouncements.filter(a => a.departureDate === key).length;
          return <button type="button" key={key} aria-pressed={selectedDate === key}
            aria-current={key === today ? 'date' : undefined}
            aria-label={`${displayDate(key)}, ${count} travel announcements`}
            onClick={() => setSelectedDate(selectedDate === key ? null : key)}
            className={`rounded-lg min-h-16 p-1 ring-1 text-sm ${selectedDate === key
              ? 'bg-brand-600 text-white ring-brand-600' : 'ring-ink-300 hover:bg-ink-100'}`}>
            <span className={key === today ? 'font-bold underline' : ''}>{i + 1}</span>
            {count > 0 && <span className="block text-xs font-semibold">{count} <span className="hidden sm:inline">trip{count === 1 ? '' : 's'}</span></span>}
          </button>;
        })}
      </div>
      {selectedDate && <button type="button" className="btn btn-ghost text-sm" onClick={() => setSelectedDate(null)}>Show all dates this month</button>}
      {q.isSuccess && visible.length === 0 && <p className="text-sm text-ink-500">No travel announcements for {selectedDate ? displayDate(selectedDate) : 'this month'}. Use Next to check upcoming months.</p>}
      <div className="space-y-3" aria-live="polite">
        {visible.map(a => <article className="rounded-xl ring-1 ring-ink-300 p-4 space-y-1 break-words" key={a.id}>
          <h3 className="font-semibold">{a.origin} &rarr; {a.destination}</h3>
          <p className="text-sm">Departure: <time dateTime={a.departureDate}>{displayDate(a.departureDate)}</time>
            {a.expectedArrivalDate && <> &middot; Expected arrival: <time dateTime={a.expectedArrivalDate}>{displayDate(a.expectedArrivalDate)}</time></>}
          </p>
          <p className="text-sm text-ink-500">Posted by {a.postedByName} &middot; {new Date(a.createdAt).toLocaleDateString()}</p>
          {a.notes && <p className="text-sm whitespace-pre-wrap">{a.notes}</p>}
          <div className="text-sm rounded-lg bg-ink-100 p-3">
            {a.totalCapacityKg == null
              ? <p>Capacity not set. The posting agent must set kilograms before senders can request space.</p>
              : <p><strong>{a.availableCapacityKg} kg available</strong> / {a.totalCapacityKg} kg total &middot; {a.approvedWeightKg} kg approved</p>}
            {a.numberOfLuggage != null && <p>{a.numberOfLuggage} luggage pieces (information only)</p>}
          </div>
          {(user?.role === 'SENDER' || user?.role === 'ADMIN') && a.totalCapacityKg != null && a.departureDate >= today &&
            <Link className="btn btn-brand text-sm" to={`/send?travel=${encodeURIComponent(a.id)}`}>Request space on this travel</Link>}
          {canPost && (user?.role === 'ADMIN' || a.postedById === user?.id) &&
            <button type="button" className="btn btn-ghost text-sm" onClick={() => {
              setEditingId(a.id);
              setCapacity({ totalCapacityKg: a.totalCapacityKg ?? '', numberOfLuggage: a.numberOfLuggage?.toString() ?? '' });
            }}>Edit capacity</button>}
          {editingId === a.id && <form className="grid gap-3 sm:grid-cols-2 pt-3"
            onSubmit={e => { e.preventDefault(); updateCapacity.mutate(a.id); }}>
            <div>
              <label className="label" htmlFor={`${id}-edit-capacity`}>Total capacity (kg, including approved weight)</label>
              <input id={`${id}-edit-capacity`} className="input" type="number" required min={Math.max(0.1, Number(a.approvedWeightKg))}
                max="99999999.99" step="0.01" value={capacity.totalCapacityKg}
                onChange={e => setCapacity({ ...capacity, totalCapacityKg: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor={`${id}-edit-luggage`}>Luggage count (optional)</label>
              <input id={`${id}-edit-luggage`} className="input" type="number" min="0" max="2147483647" step="1"
                value={capacity.numberOfLuggage} onChange={e => setCapacity({ ...capacity, numberOfLuggage: e.target.value })} />
            </div>
            <button className="btn btn-brand" disabled={updateCapacity.isPending}>Save capacity</button>
            <button type="button" className="btn btn-ghost" onClick={() => setEditingId(null)}>Cancel edit</button>
          </form>}
        </article>)}
      </div>
    </section>
  );
}
