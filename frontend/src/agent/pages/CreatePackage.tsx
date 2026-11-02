import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Check, ImagePlus, Save, Send, X } from 'lucide-react';
import { AgentApi } from '../api';
import { CustomerPicker } from '../CustomerPicker';
import type { AgentCustomer, AgentPackage } from '../types';
import { initialPackageContents, PackageItemsEditor, packageContentsError, packageContentsInput,
  PackageContentsSummary, totalPackageWeight, type PackageContents } from '../../components/PackageItemsEditor';
import { preparePackagePhoto } from '../../util/packagePhoto';
import { errorMessage, FieldError, greenButton, money, outlineButton, PageHeader, shortDate, StatusBadge, Stepper } from '../ui';

const steps = ['Package Details', 'Items & Photos', 'Review'];
const today = () => new Date().toLocaleDateString('en-CA');

interface Form {
  customer: AgentCustomer | null; pickupCity: string; destinationCity: string; contents: PackageContents;
  description: string; offeredPrice: string; availableFrom: string; deadline: string;
  photo: string | null;
}

const stepFields: (keyof Form)[][] = [
  ['customer', 'pickupCity', 'destinationCity', 'description'],
  ['contents', 'offeredPrice', 'availableFrom', 'deadline'],
  [],
];

function validate(form: Form) {
  const e: Partial<Record<keyof Form, string>> = {};
  if (!form.customer) e.customer = 'Choose a sender customer';
  else if (form.customer.type !== 'SENDER') e.customer = 'Packages can only be created for sender customers';
  else if (!form.customer.authorization) e.customer = 'This customer has no active authorization. Grant one first.';
  if (!form.pickupCity.trim()) e.pickupCity = 'Enter where the package is picked up';
  if (!form.destinationCity.trim()) e.destinationCity = 'Enter the destination';
  if (form.pickupCity.trim() && form.pickupCity.trim().toLowerCase() === form.destinationCity.trim().toLowerCase())
    e.destinationCity = 'Destination must be different from pickup';
  if (!form.description.trim()) e.description = 'Describe the items';
  const contentsError = packageContentsError(form.contents);
  if (contentsError) e.contents = contentsError;
  const price = Number(form.offeredPrice);
  if (!form.offeredPrice || !(price >= 0.01)) e.offeredPrice = 'Enter the price the sender offers';
  if (!form.availableFrom || form.availableFrom < today()) e.availableFrom = 'Choose today or a later date';
  if (!form.deadline || form.deadline < today()) e.deadline = 'Choose today or a later date';
  else if (form.availableFrom && form.deadline < form.availableFrom) e.deadline = 'Deadline must be on or after the available date';
  return e;
}

function Done({ pkg, onAnother }: { pkg: AgentPackage; onAnother: () => void }) {
  return (
    <section className="card max-w-2xl" aria-labelledby="package-done">
      <h2 id="package-done" className="text-lg font-semibold text-emerald-800 flex items-center gap-2">
        <Check className="w-5 h-5" aria-hidden="true" /> {pkg.status === 'DRAFT' ? 'Draft saved' : 'Package submitted'}
      </h2>
      <p className="text-sm text-ink-500 mt-1">
        {pkg.pickupCity} → {pkg.destinationCity} for {pkg.customerName}. {pkg.status === 'DRAFT'
          ? 'Drafts stay private until you submit them from Shipment Requests.'
          : 'An admin reviews the request before travelers can see it.'}
      </p>
      <div className="mt-3"><StatusBadge status={pkg.status} /></div>
      <div className="flex flex-wrap gap-2 mt-4">
        <Link to="/agent/requests" className={greenButton}>View shipment requests</Link>
        <button type="button" className="btn btn-ghost" onClick={onAnother}>Create another package</button>
      </div>
    </section>
  );
}

export function CreatePackagePage() {
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const preselect = params.get('customer');
  const empty: Form = { customer: null, pickupCity: '', destinationCity: '', contents: initialPackageContents(), description: '',
    offeredPrice: '', availableFrom: today(), deadline: '', photo: null };
  const [form, setForm] = useState<Form>(empty);
  const [step, setStep] = useState(0);
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({});
  const [preparingPhoto, setPreparingPhoto] = useState(false);
  const photoVersion = useRef(0);
  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm(f => ({ ...f, [key]: value }));

  const preselected = useQuery({ queryKey: ['agent', 'customer', preselect], queryFn: () => AgentApi.customer(preselect!), enabled: !!preselect });
  useEffect(() => {
    const found = preselected.data;
    if (found?.type === 'SENDER') setForm(f => (f.customer ? f : { ...f, customer: found }));
  }, [preselected.data]);

  const save = useMutation({
    mutationFn: ({ draft }: { draft: boolean }) => AgentApi.createPackage(form.customer!.id, {
      draft,
      photoDataUrl: form.photo ?? undefined,
      details: {
        pickupCity: form.pickupCity.trim(), destinationCity: form.destinationCity.trim(), itemType: form.contents.items[0].category,
        ...packageContentsInput(form.contents),
        description: form.description.trim(), weightKg: totalPackageWeight(form.contents), offeredPrice: Number(form.offeredPrice),
        availableFrom: form.availableFrom, deadline: form.deadline,
      },
    }),
    onSuccess: pkg => {
      qc.invalidateQueries({ queryKey: ['agent'] });
      toast.success(pkg.status === 'DRAFT' ? 'Draft saved' : 'Package submitted for admin review');
    },
    onError: error => toast.error(errorMessage(error)),
  });

  if (save.data) return <><PageHeader title="Create Package" /><Done pkg={save.data} onAnother={() => { save.reset(); setForm(empty); setStep(0); }} /></>;

  const check = (upTo: number) => {
    const all = validate(form);
    const relevant = Object.fromEntries(Object.entries(all).filter(([k]) => stepFields.slice(0, upTo + 1).flat().includes(k as keyof Form)));
    setErrors(relevant);
    const firstBadStep = stepFields.findIndex(fields => fields.some(f => relevant[f as keyof Form]));
    if (firstBadStep !== -1) {
      setStep(firstBadStep);
      const firstKey = stepFields[firstBadStep].find(f => relevant[f])!;
      setTimeout(() => document.getElementById(`package-${firstKey}`)?.focus(), 0);
      return false;
    }
    return true;
  };
  const next = () => { if (check(step)) setStep(step + 1); };
  const submit = (draft: boolean) => { if (check(steps.length - 1)) save.mutate({ draft }); };

  const text = (key: 'pickupCity' | 'destinationCity', label: string, placeholder: string) => (
    <div>
      <label className="label" htmlFor={`package-${key}`}>{label}</label>
      <input id={`package-${key}`} className="input" value={form[key]} maxLength={100} placeholder={placeholder}
        onChange={e => set(key, e.target.value)} aria-invalid={!!errors[key]} aria-describedby={`package-${key}-error`} />
      <FieldError id={`package-${key}-error`} message={errors[key]} />
    </div>
  );
  const field = (key: 'offeredPrice' | 'availableFrom' | 'deadline', label: string, type: string, extra: object = {}) => (
    <div>
      <label className="label" htmlFor={`package-${key}`}>{label}</label>
      <input id={`package-${key}`} className="input" type={type} value={form[key]} {...extra}
        onChange={e => set(key, e.target.value)} aria-invalid={!!errors[key]} aria-describedby={`package-${key}-error`} />
      <FieldError id={`package-${key}-error`} message={errors[key]} />
    </div>
  );

  return (
    <>
      <PageHeader title="Create Package" subtitle="Post a delivery request on behalf of one of your senders." />
      <Stepper steps={steps} current={step} />
      <form className="card max-w-3xl" noValidate onSubmit={e => { e.preventDefault(); if (step < 2) next(); else submit(false); }}>
        {step === 0 && (
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label className="label" htmlFor="package-customer">Sender customer</label>
              <CustomerPicker id="package-customer" type="SENDER" value={form.customer} onChange={c => set('customer', c)} error={errors.customer} />
              {form.customer && <FieldError id="package-customer-selected-error" message={errors.customer} />}
              {form.customer && !form.customer.authorization && (
                <p className="text-xs text-ink-500 mt-1"><Link to="/agent/authorizations" className="text-emerald-700 underline">Grant an authorization</Link> to continue.</p>
              )}
            </div>
            {text('pickupCity', 'From', 'e.g. Washington, DC')}
            {text('destinationCity', 'To', 'e.g. Addis Ababa')}
            <div className="sm:col-span-2">
              <label className="label" htmlFor="package-description">Notes / item description</label>
              <textarea id="package-description" className="input min-h-24" maxLength={1000} value={form.description}
                onChange={e => set('description', e.target.value)} placeholder="What is inside, packaging, handling notes"
                aria-invalid={!!errors.description} aria-describedby="package-description-error" />
              <FieldError id="package-description-error" message={errors.description} />
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="grid gap-4 sm:grid-cols-2">
            <div id="package-contents" tabIndex={-1} className="sm:col-span-2">
              <PackageItemsEditor value={form.contents} onChange={contents => set('contents', contents)} />
              <FieldError id="package-contents-error" message={errors.contents} />
            </div>
            {field('offeredPrice', 'Offered price (USD)', 'number', { min: 0.01, step: 0.01, inputMode: 'decimal' })}
            {field('availableFrom', 'Available from', 'date', { min: today() })}
            {field('deadline', 'Deliver by', 'date', { min: form.availableFrom || today() })}
            <div className="sm:col-span-2">
              <span className="label" id="package-photo-label">Product photo (optional)</span>
              {form.photo ? (
                <div className="flex items-center gap-3">
                  <img src={form.photo} alt="Selected package" className="w-24 h-24 object-cover rounded-xl ring-1 ring-ink-300" />
                  <button type="button" className="btn btn-ghost" onClick={() => { photoVersion.current++; set('photo', null); }}>
                    <X className="w-4 h-4" aria-hidden="true" /> Remove photo
                  </button>
                </div>
              ) : (
                <label className="flex items-center gap-3 rounded-xl border-2 border-dashed border-ink-300 p-4 cursor-pointer hover:border-emerald-600 focus-within:border-emerald-600">
                  <ImagePlus className="w-6 h-6 text-emerald-700" aria-hidden="true" />
                  <span className="text-sm">{preparingPhoto ? 'Preparing photo…' : 'Choose a JPEG or PNG photo'}</span>
                  <input type="file" className="sr-only" accept="image/jpeg,image/png" aria-labelledby="package-photo-label"
                    onChange={async e => {
                      const file = e.target.files?.[0];
                      e.target.value = '';
                      if (!file) return;
                      const version = ++photoVersion.current;
                      setPreparingPhoto(true);
                      try {
                        if (!['image/jpeg', 'image/png'].includes(file.type)) throw new Error('Choose a JPEG or PNG photo.');
                        if (file.size > 10_000_000) throw new Error('Choose a photo smaller than 10 MB.');
                        const prepared = await preparePackagePhoto(file);
                        if (photoVersion.current === version) set('photo', prepared);
                      } catch (error) {
                        toast.error(errorMessage(error));
                      } finally {
                        if (photoVersion.current === version) setPreparingPhoto(false);
                      }
                    }} />
                </label>
              )}
            </div>
          </div>
        )}

        {step === 2 && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Review</h2>
            <dl className="grid gap-3 sm:grid-cols-2 text-sm">
              {[['Customer', form.customer?.name ?? '—'], ['Route', `${form.pickupCity} → ${form.destinationCity}`],
                ['Items', String(form.contents.items.length)], ['Weight', `${totalPackageWeight(form.contents).toFixed(2)} kg`],
                ['Offered price', money(Number(form.offeredPrice))], ['Dates', `${shortDate(form.availableFrom)} – ${shortDate(form.deadline)}`],
                ['Photo', form.photo ? 'Attached' : 'None']].map(([label, value]) => (
                <div key={label} className="rounded-xl bg-ink-100 p-3"><dt className="text-ink-500">{label}</dt><dd className="font-semibold">{value}</dd></div>
              ))}
              <div className="rounded-xl bg-ink-100 p-3 sm:col-span-2"><dt className="text-ink-500">Notes</dt><dd className="whitespace-pre-wrap">{form.description}</dd></div>
            </dl>
            <div className="mt-4"><PackageContentsSummary {...packageContentsInput(form.contents)} /></div>
          </div>
        )}

        <div className="flex flex-wrap justify-between gap-2 mt-6">
          <button type="button" className="btn btn-ghost" disabled={step === 0} onClick={() => setStep(step - 1)}>Back</button>
          <div className="flex flex-wrap gap-2">
            <button type="button" className={outlineButton} disabled={save.isPending || preparingPhoto} onClick={() => submit(true)}>
              <Save className="w-4 h-4" aria-hidden="true" /> Save as Draft
            </button>
            <button type="submit" className={greenButton} disabled={save.isPending || preparingPhoto}>
              {step < 2 ? 'Continue' : <><Send className="w-4 h-4" aria-hidden="true" /> {save.isPending ? 'Submitting…' : 'Submit package'}</>}
            </button>
          </div>
        </div>
      </form>
    </>
  );
}
