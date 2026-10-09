import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AdminApi, PublicApi, ShipmentApi } from '../api/endpoints';
import { toast } from 'sonner';
import { Check } from 'lucide-react';
import { clsx } from 'clsx';

interface Form {
  destinationCountry: string;
  destinationCity: string;
  description: string;
  packageType: 'DOCUMENT' | 'MEDICINE' | 'OTHER';
  photoDataUrl: string;
  declaredWeightKg: string;
  recipientName: string;
  recipientPhone: string;
  recipientCity: string;
  recipientAddress: string;
  pickupMethod: 'ALEXANDRIA_DROPOFF' | 'PICKUP';
  pickupAddress: string;
  pickupRequestedDate: string;
  pickupTimeWindow: string;
  referralCode: string;
  senderId: string;
  guestFirstName: string;
  guestLastName: string;
  guestEmail: string;
  guestPhone: string;
}

const EMPTY: Form = {
  destinationCountry: 'Ethiopia',
  destinationCity: 'Addis Ababa',
  description: '',
  packageType: 'OTHER',
  photoDataUrl: '',
  declaredWeightKg: '',
  recipientName: '', recipientPhone: '', recipientCity: 'Addis Ababa', recipientAddress: '',
  pickupMethod: 'ALEXANDRIA_DROPOFF',
  pickupAddress: '', pickupRequestedDate: '', pickupTimeWindow: '',
  referralCode: '',
  senderId: '',
  guestFirstName: '', guestLastName: '', guestEmail: '', guestPhone: '',
};

const STEPS = ['Destination', 'Package', 'Receiver', 'Delivery', 'Review'];

export function SendPackage() {
  const { user } = useAuth();
  const info = useQuery({ queryKey: ['public-info'], queryFn: PublicApi.info });
  const isAdmin = user?.role === 'ADMIN';
  const senders = useQuery({ queryKey: ['admin-users', 'SENDER'], queryFn: () => AdminApi.users('SENDER'), enabled: isAdmin });
  const [step, setStep] = useState(0);
  const [form, setForm] = useState<Form>(EMPTY);
  const [loading, setLoading] = useState(false);
  const [created, setCreated] = useState<{ trackingNumber: string } | null>(null);

  const rate = Number(info.data?.defaultRatePerKg ?? 10);
  const pickupFee = form.pickupMethod === 'PICKUP' ? Number(info.data?.defaultPickupFee ?? 15) : 0;
  const weight = Number(form.declaredWeightKg || 0);
  const shippingFee = form.packageType === 'DOCUMENT' ? Number(info.data?.documentFee ?? 10)
    : form.packageType === 'MEDICINE' ? Number(info.data?.medicineFee ?? 15)
    : weight * rate;
  const total = shippingFee + pickupFee;

  function upd<K extends keyof Form>(k: K, v: Form[K]) { setForm({ ...form, [k]: v }); }

  // Downscale to keep the upload small
  function onPhoto(file?: File) {
    if (!file) { upd('photoDataUrl', ''); return; }
    if (!file.type.startsWith('image/')) { toast.error('Please choose an image'); return; }
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      const scale = Math.min(1, 1024 / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.width * scale);
      canvas.height = Math.round(img.height * scale);
      canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(url);
      setForm(f => ({ ...f, photoDataUrl: canvas.toDataURL('image/jpeg', 0.8) }));
    };
    img.onerror = () => { URL.revokeObjectURL(url); toast.error('Could not read image'); };
    img.src = url;
  }
  function next() { setStep(Math.min(step + 1, STEPS.length - 1)); }
  function back() { setStep(Math.max(step - 1, 0)); }

  async function submit() {
    if (isAdmin && !form.senderId) {
      toast.error('Select a sender');
      return;
    }
    setLoading(true);
    try {
      const payload = {
        senderId: isAdmin ? form.senderId : undefined,
        ...(user ? {} : {
          guestFirstName: form.guestFirstName,
          guestLastName: form.guestLastName,
          guestEmail: form.guestEmail,
          guestPhone: form.guestPhone,
        }),
        destinationCountry: form.destinationCountry,
        destinationCity: form.destinationCity,
        description: form.description,
        packageType: form.packageType,
        photoDataUrl: form.photoDataUrl || undefined,
        declaredWeightKg: Number(form.declaredWeightKg),
        pickupMethod: form.pickupMethod,
        pickupAddress: form.pickupMethod === 'PICKUP' ? form.pickupAddress : undefined,
        pickupRequestedDate: form.pickupMethod === 'PICKUP' ? form.pickupRequestedDate : undefined,
        pickupTimeWindow: form.pickupTimeWindow || undefined,
        referralCode: form.referralCode || undefined,
        recipient: {
          name: form.recipientName,
          phone: form.recipientPhone,
          city: form.recipientCity,
          address: form.recipientAddress,
        },
      };
      const s = user ? await ShipmentApi.create(payload) : await ShipmentApi.createGuest(payload);
      setCreated({ trackingNumber: s.trackingNumber });
      toast.success(`Shipment ${s.trackingNumber} created`);
    } catch (e: any) {
      toast.error(e?.response?.data?.message || 'Failed to create shipment');
    } finally {
      setLoading(false);
    }
  }

  if (created) {
    const qrUrl = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/qr/tracking/${created.trackingNumber}`;
    return (
      <div className="max-w-xl mx-auto card text-center space-y-4" data-testid="confirmation">
        <div className="w-14 h-14 rounded-full bg-emerald-500 text-white grid place-items-center mx-auto">
          <Check className="w-7 h-7" />
        </div>
        <h2 className="text-2xl font-bold">Shipment created</h2>
        <p className="text-ink-500">Save your tracking number — you’ll use it to pick up and track.</p>
        <div className="text-4xl font-display font-bold">{created.trackingNumber}</div>
        <img src={qrUrl} alt="QR Code" className="mx-auto w-56 h-56 rounded-xl ring-1 ring-ink-300" />
        <div className="flex justify-center gap-2">
          {user
            ? <Link className="btn btn-brand" to={`/track/${created.trackingNumber}`}>Track now</Link>
            : <Link className="btn btn-brand" to="/login?next=/track">Log in to track</Link>}
          {user && <Link className="btn btn-ghost" to="/my-shipments">My shipments</Link>}
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
        {step === 0 && (
          <>
            <h2 className="text-lg font-semibold">Destination</h2>
            {!user && (
              <div className="rounded-xl bg-ink-100 p-4 space-y-3">
                <div className="text-sm font-semibold">Your details <span className="font-normal text-ink-500">— or <Link to="/login?next=/send" className="text-brand-700 font-semibold">log in</Link></span></div>
                <div className="grid grid-cols-2 gap-3">
                  <div><label className="label">First name</label><input className="input" value={form.guestFirstName} onChange={e => upd('guestFirstName', e.target.value)} data-testid="guest-first" /></div>
                  <div><label className="label">Last name</label><input className="input" value={form.guestLastName} onChange={e => upd('guestLastName', e.target.value)} data-testid="guest-last" /></div>
                  <div><label className="label">Email</label><input className="input" type="email" value={form.guestEmail} onChange={e => upd('guestEmail', e.target.value)} data-testid="guest-email" /></div>
                  <div><label className="label">Phone</label><input className="input" value={form.guestPhone} onChange={e => upd('guestPhone', e.target.value)} data-testid="guest-phone" /></div>
                </div>
              </div>
            )}
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
            <div><label className="label">Estimated weight (kg)</label><input className="input" type="number" step="0.1" min="0.1" value={form.declaredWeightKg} onChange={e => upd('declaredWeightKg', e.target.value)} data-testid="pkg-weight" /></div>
            <div>
              <label className="label">Package photo (optional)</label>
              <input className="input" type="file" accept="image/*" onChange={e => onPhoto(e.target.files?.[0])} data-testid="pkg-photo" />
              {form.photoDataUrl && <img src={form.photoDataUrl} alt="Package preview" className="mt-2 h-32 rounded-xl object-cover ring-1 ring-ink-300" />}
            </div>
          </>
        )}
        {step === 2 && (
          <>
            <h2 className="text-lg font-semibold">Receiver in Ethiopia</h2>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label">Name</label><input className="input" value={form.recipientName} onChange={e => upd('recipientName', e.target.value)} required data-testid="rcpt-name" /></div>
              <div><label className="label">Phone</label><input className="input" value={form.recipientPhone} onChange={e => upd('recipientPhone', e.target.value)} required data-testid="rcpt-phone" /></div>
              <div><label className="label">City</label><input className="input" value={form.recipientCity} onChange={e => upd('recipientCity', e.target.value)} required data-testid="rcpt-city" /></div>
              <div><label className="label">Address</label><input className="input" value={form.recipientAddress} onChange={e => upd('recipientAddress', e.target.value)} data-testid="rcpt-addr" /></div>
            </div>
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
              <div><div className="text-ink-500 text-xs">Receiver</div><div className="font-semibold">{form.recipientName} · {form.recipientPhone}</div></div>
              <div><div className="text-ink-500 text-xs">Method</div><div className="font-semibold">{form.pickupMethod === 'PICKUP' ? 'Pickup' : 'Alexandria Drop-off'}</div></div>
              <div><div className="text-ink-500 text-xs">Payment</div><div className="font-semibold">To be collected</div></div>
            </div>
          </>
        )}

        <div className="flex items-center justify-between pt-2">
          <button className="btn btn-ghost" onClick={back} disabled={step === 0}>Back</button>
          {step < STEPS.length - 1 ? (
            <button className="btn btn-primary" onClick={next}
              disabled={
                (step === 0 && !user && (!form.guestFirstName || !form.guestLastName || !form.guestEmail || !form.guestPhone)) ||
                (step === 1 && !form.declaredWeightKg) ||
                (step === 2 && (!form.recipientName || !form.recipientPhone || !form.recipientCity))
              }
              data-testid="step-next">
              Next
            </button>
          ) : (
            <button className="btn btn-brand" onClick={submit} disabled={loading} data-testid="confirm-shipment">
              {loading ? 'Creating…' : 'Create shipment'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
