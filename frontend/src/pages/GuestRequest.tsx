import { useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { GuestRequestsApi } from '../api/guestRequests';
import { initialRequestForm, MarketRequestFields } from '../components/MarketRequestFields';
import { preparePackagePhoto } from '../util/packagePhoto';
import { DemoNotice } from './Marketplace';

function errorMessage(error: unknown) {
  if (isAxiosError<{ message?: string }>(error)) return error.response?.data?.message || 'Could not submit your request. Please retry.';
  return error instanceof Error ? error.message : 'Could not submit your request. Please retry.';
}

export function GuestRequest() {
  const { hash } = useLocation();
  const token = new URLSearchParams(hash.slice(1)).get('token') || '';
  return <GuestRequestForm key={token} token={token} />;
}

function GuestRequestForm({ token }: { token: string }) {
  const validToken = /^[A-Za-z0-9_-]{43}$/.test(token);
  const check = useQuery({ queryKey: ['guest-request-status', token], queryFn: () => GuestRequestsApi.check(token),
    enabled: validToken, retry: false, gcTime: 0, refetchOnWindowFocus: false });
  const [form, setForm] = useState(initialRequestForm);
  const [contact, setContact] = useState({ name: '', email: '', phone: '' });
  const [photo, setPhoto] = useState('');
  const [photoError, setPhotoError] = useState('');
  const [preparingPhoto, setPreparingPhoto] = useState(false);
  const photoVersion = useRef(0);
  const mutation = useMutation({
    mutationFn: () => GuestRequestsApi.submit({ token, ...contact, photoDataUrl: photo,
      request: { ...form, weightKg: Number(form.weightKg), offeredPrice: Number(form.offeredPrice) } }),
    onSuccess: () => { setContact({ name: '', email: '', phone: '' }); setPhoto(''); setForm(initialRequestForm()); },
  });
  if (!validToken) return <p role="alert">This private link is incomplete. Please ask us for a new guest request link.</p>;
  if (check.isPending) return <p role="status">Checking your private link...</p>;
  if (check.isError) return <div role="alert">Could not open this private link. It may be invalid, or the service may be unavailable.
    <button className="btn btn-ghost" onClick={() => check.refetch()}>Retry</button><p>Please contact us if it still does not open.</p></div>;
  const result = mutation.data ?? check.data;
  if (result.status !== 'OPEN') return <section className="card max-w-2xl mx-auto space-y-3" role="status">
    <h1 className="text-2xl font-bold">{result.status === 'SUBMITTED' ? 'Request received' : 'Link unavailable'}</h1>
    <p>{result.message}</p>
    {result.status === 'SUBMITTED' && <p>You do not need an account. Our team will use the email or phone number you provided to coordinate with you.</p>}
  </section>;
  return <div className="max-w-2xl mx-auto space-y-5">
    <h1 className="text-3xl font-bold">Send a package as a guest</h1>
    <p>No account or password is required. Add your contact details and a clear photo of the product. We will contact you once we find a traveler.</p>
    <p className="text-sm">Keep this link private. It expires seven days after creation and can be used for one request. Your contact details are visible only to our administrators; travelers review the package details and photo. Do not put names, phone numbers, addresses, or email in the description or photo.</p>
    <DemoNotice />
    <form className="card grid sm:grid-cols-2 gap-4" onSubmit={e => {
      e.preventDefault();
      if (!photo) { setPhotoError('Please add a product photo.'); return; }
      mutation.mutate();
    }}>
      <fieldset className="contents" disabled={mutation.isPending}>
        <label className="label sm:col-span-2">Your full name<input className="input" autoComplete="name" required maxLength={120} value={contact.name} onChange={e => setContact({ ...contact, name: e.target.value })} /></label>
        <label className="label">Email<input className="input" type="email" autoComplete="email" required maxLength={254} value={contact.email} onChange={e => setContact({ ...contact, email: e.target.value })} /></label>
        <label className="label">Phone (include country code)<input className="input" type="tel" autoComplete="tel" required maxLength={40} value={contact.phone} onChange={e => setContact({ ...contact, phone: e.target.value })} /></label>
        <MarketRequestFields form={form} onChange={setForm} />
        <label className="label sm:col-span-2">Product photo (required)
          <input className="input" type="file" accept="image/jpeg,image/png" required={!photo}
            aria-describedby="guest-photo-help" onChange={async e => {
              const file = e.target.files?.[0];
              const version = ++photoVersion.current;
              setPhoto(''); setPhotoError('');
              if (!file) { setPreparingPhoto(false); return; }
              setPreparingPhoto(true);
              try {
                if (!['image/jpeg', 'image/png'].includes(file.type)) throw new Error('Choose a JPEG or PNG photo.');
                if (file.size > 10_000_000) throw new Error('Choose a photo smaller than 10 MB.');
                const prepared = await preparePackagePhoto(file);
                if (photoVersion.current === version) setPhoto(prepared);
              } catch (error) {
                if (photoVersion.current === version) setPhotoError(errorMessage(error));
              } finally {
                if (photoVersion.current === version) setPreparingPhoto(false);
              }
            }} />
          <span id="guest-photo-help" className="text-sm font-normal">JPEG or PNG, up to 10 MB. We resize it before uploading (under 2 MB).</span>
        </label>
        {preparingPhoto && <p role="status" className="sm:col-span-2">Preparing photo...</p>}
        {photoError && <p role="alert" className="text-red-700 sm:col-span-2">{photoError}</p>}
        {photo && <img src={photo} alt="Product photo preview" className="max-h-56 max-w-full rounded-lg object-contain sm:col-span-2" />}
        {mutation.isError && <p role="alert" className="text-red-700 sm:col-span-2">{errorMessage(mutation.error)} Your entries have been kept so you can retry.</p>}
        <button className="btn btn-brand sm:col-span-2" disabled={preparingPhoto || mutation.isPending || !photo}>
          {mutation.isPending ? 'Submitting...' : 'Submit package request'}
        </button>
      </fieldset>
    </form>
  </div>;
}
