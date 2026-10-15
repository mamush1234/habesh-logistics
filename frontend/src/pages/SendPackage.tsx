import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AdminApi, PublicApi, ShipmentApi, TravelAnnouncementApi } from '../api/endpoints';
import { toast } from 'sonner';
import { Check } from 'lucide-react';
import { clsx } from 'clsx';
import { preparePackagePhoto } from '../util/packagePhoto';
import { PrivateQr } from '../components/PrivateQr';

interface Form {
  destinationCountry: string;
  destinationCity: string;
  description: string;
  packageType: 'DOCUMENT' | 'MEDICINE' | 'OTHER';
  photoDataUrls: string[];
  declaredWeightKg: string;
  recipients: { name: string; phone: string; city: string; address: string }[];
  pickupMethod: 'ALEXANDRIA_DROPOFF' | 'PICKUP';
  pickupAddress: string;
  pickupRequestedDate: string;
  pickupTimeWindow: string;
  referralCode: string;
  senderId: string;
  travelAnnouncementId: string;
}

const EMPTY: Form = {
  destinationCountry: 'Ethiopia',
  destinationCity: 'Addis Ababa',
  description: '',
  packageType: 'OTHER',
  photoDataUrls: [],
  declaredWeightKg: '',
  recipients: [{ name: '', phone: '', city: 'Addis Ababa', address: '' }],
  pickupMethod: 'ALEXANDRIA_DROPOFF',
  pickupAddress: '', pickupRequestedDate: '', pickupTimeWindow: '',
  referralCode: '',
  senderId: '',
  travelAnnouncementId: '',
};

const STEPS = ['Destination', 'Package', 'Receiver', 'Delivery', 'Review'];

export function SendPackage() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const travel = useQuery({ queryKey: ['travel-announcements'], queryFn: TravelAnnouncementApi.list, refetchInterval: 30_000 });
  const info = useQuery({ queryKey: ['public-info'], queryFn: PublicApi.info });
  const isAdmin = user?.role === 'ADMIN';
  const senders = useQuery({ queryKey: ['admin-users', 'SENDER'], queryFn: () => AdminApi.users('SENDER'), enabled: isAdmin });
  const [step, setStep] = useState(0);
  const [form, setForm] = useState<Form>(() => ({ ...EMPTY, travelAnnouncementId: params.get('travel') ?? '' }));
  const [loading, setLoading] = useState(false);
  const [preparingPhotos, setPreparingPhotos] = useState(false);
  const [created, setCreated] = useState<{ trackingNumber: string } | null>(null);

  const rate = Number(info.data?.defaultRatePerKg ?? 10);
  const pickupFee = form.pickupMethod === 'PICKUP' ? Number(info.data?.defaultPickupFee ?? 15) : 0;
  const weight = Number(form.declaredWeightKg || 0);
  const validWeight = Number.isFinite(weight) && weight >= 0.1 && weight <= 99999999.99
    && !/\.\d{3,}/.test(form.declaredWeightKg);
  const shippingFee = form.packageType === 'DOCUMENT' ? Number(info.data?.documentFee ?? 10)
    : form.packageType === 'MEDICINE' ? Number(info.data?.medicineFee ?? 15)
    : weight * rate;
  const total = shippingFee + pickupFee;
  const today = new Date();
  const todayKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  const bookableTravel = travel.data?.filter(a => a.departureDate >= todayKey && a.totalCapacityKg != null) ?? [];
  const selectedTravel = bookableTravel.find(a => a.id === form.travelAnnouncementId);
  const invalidTravel = Boolean(form.travelAnnouncementId) && !selectedTravel;
  const remainingAfterApproval = selectedTravel?.availableCapacityKg == null ? null : Number(selectedTravel.availableCapacityKg) - weight;

  function upd<K extends keyof Form>(k: K, v: Form[K]) { setForm(current => ({ ...current, [k]: v })); }

  async function onPhotos(files: File[]) {
    if (!files.length) return;
    if (form.photoDataUrls.length + files.length > 5) {
      toast.error('A package can have up to 5 pictures');
      return;
    }
    setPreparingPhotos(true);
    try {
      const photos = await Promise.all(files.map(preparePackagePhoto));
      setForm(f => ({ ...f, photoDataUrls: [...f.photoDataUrls, ...photos] }));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Could not prepare image');
    } finally {
      setPreparingPhotos(false);
    }
  }
  function next() { setStep(Math.min(step + 1, STEPS.length - 1)); }
  function back() { setStep(Math.max(step - 1, 0)); }

  async function submit() {
    if (isAdmin && !form.senderId) {
      toast.error('Select a sender');
      return;
    }
    if (invalidTravel) {
      toast.error('Select an available travel or choose no travel preference');
      return;
    }
    if (!validWeight) {
      toast.error('Enter a weight of at least 0.1 kg with no more than two decimal places');
      return;
    }
    setLoading(true);
    try {
      const payload = {
        senderId: isAdmin ? form.senderId : undefined,
        travelAnnouncementId: form.travelAnnouncementId || undefined,
        destinationCountry: form.destinationCountry,
        destinationCity: form.destinationCity,
        description: form.description,
        packageType: form.packageType,
        photoDataUrls: form.photoDataUrls,
        declaredWeightKg: Number(form.declaredWeightKg),
        pickupMethod: form.pickupMethod,
        pickupAddress: form.pickupMethod === 'PICKUP' ? form.pickupAddress : undefined,
        pickupRequestedDate: form.pickupMethod === 'PICKUP' ? form.pickupRequestedDate : undefined,
        pickupTimeWindow: form.pickupTimeWindow || undefined,
        referralCode: form.referralCode || undefined,
        recipient: form.recipients[0],
        additionalRecipients: form.recipients.slice(1),
      };
      const s = await ShipmentApi.create(payload);
      qc.invalidateQueries({ queryKey: ['travel-requests'] });
      qc.invalidateQueries({ queryKey: ['my-shipments'] });
      setCreated({ trackingNumber: s.trackingNumber });
      toast.success(`Shipment ${s.trackingNumber} created`);
    } catch (e: any) {
      toast.error(e?.response?.data?.message || 'Failed to create shipment');
    } finally {
      setLoading(false);
    }
  }

  if (created) {
    return (
      <div className="max-w-xl mx-auto card text-center space-y-4" data-testid="confirmation">
        <div className="w-14 h-14 rounded-full bg-emerald-500 text-white grid place-items-center mx-auto">
          <Check className="w-7 h-7" />
        </div>
        <h2 className="text-2xl font-bold">Shipment created</h2>
        <p className="text-ink-500">Save your tracking number — you’ll use it to pick up and track.</p>
        {form.travelAnnouncementId && <p className="text-sm">Travel space requested. Your kilograms will be reserved only when the posting agent approves.</p>}
        <div className="text-4xl font-display font-bold">{created.trackingNumber}</div>
        <PrivateQr kind="tracking" value={created.trackingNumber} inline />
        <div className="flex justify-center gap-2">
          <Link className="btn btn-brand" to={`/track/${created.trackingNumber}`}>Track now</Link>
          <Link className="btn btn-ghost" to="/my-shipments">My shipments</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-3xl font-bold">Send a package</h1>

      <ol className="mt-6 flex items-center gap-2">
        {STEPS.map((s, i) => (
          <li key={s} className="flex items-center gap-2 flex-1">
            <span className={clsx('step-dot',
              i < step ? 'bg-emerald-500 text-white' :
              i === step ? 'bg-ink-900 text-white' :
              'bg-ink-100 text-ink-500')}>{i + 1}</span>
            <span className={clsx('text-xs font-semibold hidden sm:inline',
              i <= step ? 'text-ink-900' : 'text-ink-500')}>{s}</span>
            {i < STEPS.length - 1 && <span className={clsx('h-px flex-1', i < step ? 'bg-emerald-500' : 'bg-ink-300')} />}
          </li>
        ))}
      </ol>

      <div className="card mt-6 space-y-4" data-testid="send-form">
        <div className="space-y-2 rounded-xl bg-ink-100 p-4">
          <label className="label" htmlFor="shipment-travel">Choose travel (optional)</label>
          <select id="shipment-travel" className="input" value={form.travelAnnouncementId}
            onChange={e => upd('travelAnnouncementId', e.target.value)} disabled={loading}>
            <option value="">No travel preference</option>
            {invalidTravel && <option value={form.travelAnnouncementId}>Selected travel is unavailable</option>}
            {bookableTravel.map(a => <option key={a.id} value={a.id}>
              {a.origin} to {a.destination} - {a.departureDate} - {a.availableCapacityKg} kg available ({a.postedByName})
            </option>)}
          </select>
          {travel.isPending && <p role="status" className="text-sm">Loading travel capacity...</p>}
          {travel.isError && <p role="alert" className="text-sm text-red-700">
            Could not load travel. <button type="button" className="underline" onClick={() => travel.refetch()}>Retry</button>
          </p>}
          {invalidTravel && <p role="alert" className="text-sm text-red-700">Choose an available travel or no travel preference.</p>}
          {selectedTravel && <div className="text-sm space-y-1" aria-live="polite">
            <p><strong>{selectedTravel.availableCapacityKg} kg available</strong> / {selectedTravel.totalCapacityKg} kg total
              {selectedTravel.numberOfLuggage != null && <> &middot; {selectedTravel.numberOfLuggage} luggage pieces</>}
            </p>
            {weight > 0 && remainingAfterApproval != null && (remainingAfterApproval >= 0
              ? <p>Your request: {weight} kg. If approved now, {remainingAfterApproval.toFixed(2)} kg would remain.</p>
              : <p className="text-red-700">Your {weight} kg request exceeds current space by {(-remainingAfterApproval).toFixed(2)} kg. The agent cannot approve unless capacity becomes available.</p>)}
            <p>Pending requests do not reserve space. Availability is checked again at approval.</p>
          </div>}
        </div>
        {step === 0 && (
          <>
            <h2 className="text-lg font-semibold">Destination</h2>
            {isAdmin && (
              <div>
                <label className="label">Sender (on behalf of)</label>
                <select className="input" value={form.senderId} onChange={e => upd('senderId', e.target.value)} data-testid="admin-sender">
                  <option value="">Select a sender…</option>
                  {senders.data?.map(s => (
                    <option key={s.id} value={s.id}>{s.firstName} {s.lastName} ({s.email})</option>
                  ))}
                </select>
              </div>
            )}
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label">Country</label><input className="input" value={form.destinationCountry} onChange={e => upd('destinationCountry', e.target.value)} /></div>
              <div><label className="label">City</label><input className="input" value={form.destinationCity} onChange={e => upd('destinationCity', e.target.value)} data-testid="dest-city" /></div>
            </div>
          </>
        )}
        {step === 1 && (
          <>
            <h2 className="text-lg font-semibold">Package information</h2>
            <div>
              <label className="label">Package type</label>
              <select className="input" value={form.packageType} onChange={e => upd('packageType', e.target.value as Form['packageType'])} data-testid="pkg-type">
                <option value="DOCUMENT">Documents — ${Number(info.data?.documentFee ?? 10).toFixed(2)} flat</option>
                <option value="MEDICINE">Medicine / vitamins — ${Number(info.data?.medicineFee ?? 15).toFixed(2)} flat</option>
                <option value="OTHER">Other — ${rate.toFixed(2)} per kg</option>
              </select>
            </div>
            <div><label className="label">Description</label><input className="input" placeholder="Clothing, gifts, documents…" value={form.description} onChange={e => upd('description', e.target.value)} data-testid="pkg-desc" /></div>
            <div><label className="label">Estimated weight (kg)</label><input className="input" type="number" step="0.01" min="0.1" max="99999999.99" value={form.declaredWeightKg} onChange={e => upd('declaredWeightKg', e.target.value)} data-testid="pkg-weight" /></div>
            {form.declaredWeightKg && !validWeight && <p role="alert" className="text-sm text-red-700">Enter at least 0.1 kg with no more than two decimal places.</p>}
            <div>
              <label className="label">Package pictures (optional, up to 5) - {form.photoDataUrls.length}/5</label>
              <input className="input" type="file" accept="image/*" multiple
                disabled={preparingPhotos || form.photoDataUrls.length >= 5}
                onChange={e => {
                  const files = Array.from(e.target.files ?? []);
                  e.target.value = '';
                  void onPhotos(files);
                }} data-testid="pkg-photo" />
              {preparingPhotos && <p role="status" className="text-sm">Preparing pictures...</p>}
              <div className="flex flex-wrap gap-3 mt-2">
                {form.photoDataUrls.map((src, index) => <div key={index}>
                  <img src={src} alt={`Package preview ${index + 1}`} className="h-24 w-24 rounded-xl object-contain ring-1 ring-ink-300" />
                  <button type="button" className="text-xs text-red-600 underline" disabled={preparingPhotos}
                    onClick={() => upd('photoDataUrls', form.photoDataUrls.filter((_, i) => i !== index))}>Remove picture {index + 1}</button>
                </div>)}
              </div>
            </div>
          </>
        )}
        {step === 2 && (
          <>
            <h2 className="text-lg font-semibold">Recipients in Ethiopia</h2>
            {form.recipients.map((recipient, index) => <fieldset key={index} className="rounded-xl border border-ink-300 p-3 space-y-3">
              <legend className="font-semibold px-1">Recipient {index + 1}</legend>
              <div className="grid grid-cols-2 gap-3">
                {(['name', 'phone', 'city', 'address'] as const).map(field => <div key={field}>
                  <label className="label" htmlFor={`recipient-${index}-${field}`}>{field[0].toUpperCase() + field.slice(1)}</label>
                  <input id={`recipient-${index}-${field}`} className="input" value={recipient[field]}
                    onChange={e => upd('recipients', form.recipients.map((r, i) => i === index ? { ...r, [field]: e.target.value } : r))}
                    required={field !== 'address'} data-testid={`rcpt-${field === 'address' ? 'addr' : field}${index ? `-${index + 1}` : ''}`} />
                </div>)}
              </div>
              {form.recipients.length > 1 && <button type="button" className="btn btn-ghost text-red-600 text-xs"
                onClick={() => upd('recipients', form.recipients.filter((_, i) => i !== index))}>Remove recipient {index + 1}</button>}
            </fieldset>)}
            <button type="button" className="btn btn-ghost" onClick={() => upd('recipients',
              [...form.recipients, { name: '', phone: '', city: form.destinationCity, address: '' }])}>Add recipient</button>
          </>
        )}
        {step === 3 && (
          <>
            <h2 className="text-lg font-semibold">How do we receive the package?</h2>
            <div className="grid md:grid-cols-2 gap-3">
              <button type="button" onClick={() => upd('pickupMethod', 'ALEXANDRIA_DROPOFF')}
                className={clsx('text-left p-4 rounded-xl ring-1 transition',
                  form.pickupMethod === 'ALEXANDRIA_DROPOFF' ? 'ring-brand-500 bg-brand-50' : 'ring-ink-300 bg-white')}
                data-testid="method-dropoff">
                <div className="font-semibold">Alexandria Drop-off</div>
                <p className="text-sm text-ink-500 mt-1">Bring the package to our Alexandria location. No pickup fee.</p>
                <p className="text-xs mt-2">{info.data?.alexandriaAddress} · {info.data?.alexandriaHours}</p>
              </button>
              <button type="button" onClick={() => upd('pickupMethod', 'PICKUP')}
                className={clsx('text-left p-4 rounded-xl ring-1 transition',
                  form.pickupMethod === 'PICKUP' ? 'ring-brand-500 bg-brand-50' : 'ring-ink-300 bg-white')}
                data-testid="method-pickup">
                <div className="font-semibold">Pickup</div>
                <p className="text-sm text-ink-500 mt-1">We collect from your address. Pickup fee ${info.data?.defaultPickupFee}.</p>
              </button>
            </div>
            {form.pickupMethod === 'PICKUP' && (
              <div className="grid grid-cols-2 gap-3 mt-2">
                <div className="col-span-2"><label className="label">Pickup address</label><input className="input" value={form.pickupAddress} onChange={e => upd('pickupAddress', e.target.value)} required /></div>
                <div><label className="label">Preferred date</label><input className="input" type="date" value={form.pickupRequestedDate} onChange={e => upd('pickupRequestedDate', e.target.value)} required /></div>
                <div><label className="label">Time window</label><input className="input" placeholder="e.g. 2pm – 5pm" value={form.pickupTimeWindow} onChange={e => upd('pickupTimeWindow', e.target.value)} /></div>
              </div>
            )}
            <div className="pt-2">
              <label className="label">Agent ID / referral code (optional)</label>
              <input className="input" value={form.referralCode} onChange={e => upd('referralCode', e.target.value.toUpperCase())} placeholder="BETI2026" data-testid="referral-code" />
            </div>
          </>
        )}
        {step === 4 && (
          <>
            <h2 className="text-lg font-semibold">Review & confirm</h2>
            <div className="rounded-xl bg-ink-100 p-4 text-sm">
              <div className="flex justify-between py-1"><span>{form.packageType === 'OTHER' ? 'Weight × rate' : 'Flat fee'}</span><span>{form.packageType === 'OTHER' ? `${weight} kg × $${rate.toFixed(2)}` : form.packageType === 'DOCUMENT' ? 'Documents' : 'Medicine / vitamins'}</span></div>
              <div className="flex justify-between py-1"><span>Shipping fee</span><span>${shippingFee.toFixed(2)}</span></div>
              <div className="flex justify-between py-1"><span>Pickup fee</span><span>${pickupFee.toFixed(2)}</span></div>
              <div className="flex justify-between pt-2 mt-2 border-t border-ink-300 font-bold text-lg"><span>Total</span><span>${total.toFixed(2)}</span></div>
            </div>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div><div className="text-ink-500 text-xs">Destination</div><div className="font-semibold">{form.destinationCity}, {form.destinationCountry}</div></div>
              <div><div className="text-ink-500 text-xs">Recipients</div>
                {form.recipients.map((recipient, index) => <div key={index} className="font-semibold">{recipient.name} · {recipient.phone}</div>)}
              </div>
              <div><div className="text-ink-500 text-xs">Method</div><div className="font-semibold">{form.pickupMethod === 'PICKUP' ? 'Pickup' : 'Alexandria Drop-off'}</div></div>
              <div><div className="text-ink-500 text-xs">Payment</div><div className="font-semibold">To be collected</div></div>
            </div>
            {form.photoDataUrls.length > 0 && <div className="flex flex-wrap gap-2">
              {form.photoDataUrls.map((src, index) => <img key={index} src={src} alt={`Package preview ${index + 1}`}
                className="w-20 h-20 object-contain rounded-lg" />)}
            </div>}
          </>
        )}

        <div className="flex items-center justify-between pt-2">
          <button className="btn btn-ghost" onClick={back} disabled={step === 0 || preparingPhotos}>Back</button>
          {step < STEPS.length - 1 ? (
            <button className="btn btn-primary" onClick={next}
              disabled={
                preparingPhotos ||
                (step === 1 && !validWeight) ||
                (step === 2 && form.recipients.some(r => !r.name.trim() || !r.phone.trim() || !r.city.trim()))
              }
              data-testid="step-next">
              Next
            </button>
          ) : (
            <button className="btn btn-brand" onClick={submit} disabled={loading || invalidTravel} data-testid="confirm-shipment">
              {loading ? 'Creating…' : 'Create shipment'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
