import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { clsx } from 'clsx';
import { Check, Copy, Package, Plane } from 'lucide-react';
import { AgentApi } from '../api';
import type { AuthorizationMethod, CustomerCreated, CustomerType } from '../types';
import { authorizationMethods } from './Authorizations';
import { errorMessage, FieldError, greenButton, outlineButton, PageHeader, Stepper, titleCase } from '../ui';

const steps = ['Customer Type', 'Personal Info', 'Review'];
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface Form {
  customerType: CustomerType | null; firstName: string; lastName: string; email: string; phone: string;
  method: AuthorizationMethod; validDays: number; consentNote: string; confirmed: boolean;
}

function validateInfo(form: Form) {
  const errors: Record<string, string> = {};
  if (!form.firstName.trim()) errors.firstName = 'First name is required';
  if (!form.lastName.trim()) errors.lastName = 'Last name is required';
  if (!emailPattern.test(form.email.trim())) errors.email = 'Enter a valid email address';
  if (form.phone.replace(/\D/g, '').length < 7) errors.phone = 'Enter a valid phone number';
  if (!Number.isInteger(form.validDays) || form.validDays < 1 || form.validDays > 365) errors.validDays = 'Enter 1 to 365 days';
  if (!form.confirmed) errors.confirmed = 'You must confirm the customer authorized you before continuing';
  return errors;
}

function Credentials({ result }: { result: CustomerCreated }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(`Email: ${result.credentials.email}\nTemporary password: ${result.credentials.temporaryPassword}`);
      setCopied(true);
    } catch { toast.error('Copy failed. Select the text and copy it manually.'); }
  };
  const isSender = result.customer.type === 'SENDER';
  return (
    <section className="card max-w-2xl" aria-labelledby="created-heading">
      <h2 id="created-heading" className="text-lg font-semibold text-emerald-800 flex items-center gap-2">
        <Check className="w-5 h-5" aria-hidden="true" /> {result.customer.name} is registered
      </h2>
      <p className="text-sm text-ink-500 mt-1">
        Share these one-time sign-in details with the customer. They must choose a new password when they first sign in.
        This password will not be shown again.
      </p>
      <dl className="mt-4 grid gap-2 rounded-xl bg-ink-100 p-4 font-mono text-sm" data-testid="customer-credentials">
        <div><dt className="inline text-ink-500">Email: </dt><dd className="inline">{result.credentials.email}</dd></div>
        <div><dt className="inline text-ink-500">Temporary password: </dt><dd className="inline">{result.credentials.temporaryPassword}</dd></div>
      </dl>
      <div className="flex flex-wrap gap-2 mt-4">
        <button type="button" className={outlineButton} onClick={copy}>
          <Copy className="w-4 h-4" aria-hidden="true" /> {copied ? 'Copied' : 'Copy details'}
        </button>
        <Link to={isSender ? `/agent/packages/new?customer=${result.customer.id}` : `/agent/trips/new?customer=${result.customer.id}`} className={greenButton}>
          {isSender ? <Package className="w-4 h-4" aria-hidden="true" /> : <Plane className="w-4 h-4" aria-hidden="true" />}
          {isSender ? 'Create a package' : 'Post a trip'}
        </Link>
        <Link to="/agent/customers" className="btn btn-ghost">Back to customers</Link>
      </div>
    </section>
  );
}

export function RegisterCustomerPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [step, setStep] = useState(0);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [form, setForm] = useState<Form>({ customerType: null, firstName: '', lastName: '', email: '', phone: '',
    method: 'IN_PERSON', validDays: 90, consentNote: '', confirmed: false });
  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm(f => ({ ...f, [key]: value }));
  const create = useMutation({
    mutationFn: AgentApi.createCustomer,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['agent'] }); toast.success('Customer registered'); },
    onError: error => toast.error(errorMessage(error)),
  });

  if (create.data) return <><PageHeader title="Register Customer" /><Credentials result={create.data} /></>;

  const next = (event: FormEvent) => {
    event.preventDefault();
    if (step === 0) {
      if (!form.customerType) { setErrors({ customerType: 'Choose sender or traveler' }); return; }
      setErrors({}); setStep(1); return;
    }
    if (step === 1) {
      const found = validateInfo(form);
      setErrors(found);
      if (Object.keys(found).length) {
        document.getElementById(`customer-${Object.keys(found)[0]}`)?.focus();
        return;
      }
      setStep(2); return;
    }
    create.mutate({
      customerType: form.customerType!, firstName: form.firstName.trim(), lastName: form.lastName.trim(),
      email: form.email.trim().toLowerCase(), phone: form.phone.trim(),
      authorization: { confirmed: form.confirmed, method: form.method, validDays: form.validDays,
        consentNote: form.consentNote.trim() || undefined },
    });
  };

  const input = (key: 'firstName' | 'lastName' | 'email' | 'phone', label: string, type = 'text', autoComplete?: string) => (
    <div>
      <label className="label" htmlFor={`customer-${key}`}>{label}</label>
      <input id={`customer-${key}`} className="input" type={type} value={form[key]} autoComplete={autoComplete}
        maxLength={{ firstName: 100, lastName: 100, email: 254, phone: 40 }[key]}
        onChange={e => set(key, e.target.value)} aria-invalid={!!errors[key]} aria-describedby={`customer-${key}-error`} required />
      <FieldError id={`customer-${key}-error`} message={errors[key]} />
    </div>
  );

  return (
    <>
      <PageHeader title="Register Customer" subtitle="Create an account for a sender or traveler you will help." />
      <Stepper steps={steps} current={step} />
      <form className="card max-w-3xl" onSubmit={next} noValidate>
        {step === 0 && (
          <fieldset>
            <legend className="text-lg font-semibold mb-4">What kind of customer is this?</legend>
            <div className="grid gap-4 sm:grid-cols-2">
              {([['SENDER', 'Sender', 'Sends packages through travelers', Package], ['TRAVELER', 'Traveler', 'Carries packages on their trips', Plane]] as const)
                .map(([value, label, help, Icon]) => (
                  <label key={value} className={clsx('cursor-pointer rounded-2xl p-5 ring-2 flex gap-3 items-start focus-within:ring-emerald-600',
                    form.customerType === value ? 'ring-emerald-600 bg-emerald-50' : 'ring-ink-300/60 hover:ring-emerald-300')}>
                    <input type="radio" name="customerType" value={value} className="sr-only" id={value === 'SENDER' ? 'customer-customerType' : undefined}
                      checked={form.customerType === value} onChange={() => set('customerType', value)} />
                    <Icon className="w-6 h-6 text-emerald-700 shrink-0" aria-hidden="true" />
                    <span><span className="block font-semibold">{label}</span><span className="text-sm text-ink-500">{help}</span></span>
                  </label>
                ))}
            </div>
            <FieldError id="customer-type-error" message={errors.customerType} />
          </fieldset>
        )}

        {step === 1 && (
          <div className="grid gap-4 sm:grid-cols-2">
            {input('firstName', 'First name', 'text', 'off')}
            {input('lastName', 'Last name', 'text', 'off')}
            {input('email', 'Email', 'email', 'off')}
            {input('phone', 'Phone', 'tel', 'off')}
            <fieldset className="sm:col-span-2 grid gap-4 sm:grid-cols-2 rounded-2xl bg-emerald-50/60 ring-1 ring-emerald-200 p-4">
              <legend className="px-1 text-sm font-semibold text-emerald-900">Customer authorization</legend>
              <div>
                <label className="label" htmlFor="customer-method">How was consent given?</label>
                <select id="customer-method" className="input" value={form.method} onChange={e => set('method', e.target.value as AuthorizationMethod)}>
                  {authorizationMethods.map(m => <option key={m} value={m}>{titleCase(m)}</option>)}
                </select>
              </div>
              <div>
                <label className="label" htmlFor="customer-validDays">Valid for (days)</label>
                <input id="customer-validDays" className="input" type="number" min={1} max={365} value={form.validDays}
                  onChange={e => set('validDays', Number(e.target.value))} aria-invalid={!!errors.validDays} aria-describedby="customer-validDays-error" />
                <FieldError id="customer-validDays-error" message={errors.validDays} />
              </div>
              <div className="sm:col-span-2">
                <label className="label" htmlFor="customer-note">Consent note (optional)</label>
                <input id="customer-note" className="input" maxLength={500} value={form.consentNote}
                  onChange={e => set('consentNote', e.target.value)} placeholder="e.g. Signed consent form, kept at office" />
              </div>
              <div className="sm:col-span-2">
                <label className="flex items-start gap-2 text-sm">
                  <input id="customer-confirmed" type="checkbox" className="mt-1 accent-emerald-700" checked={form.confirmed}
                    onChange={e => set('confirmed', e.target.checked)} aria-invalid={!!errors.confirmed} aria-describedby="customer-confirmed-error" />
                  <span>I confirm this customer has authorized me to create an account and act on their behalf. <span className="text-rose-700">*</span></span>
                </label>
                <FieldError id="customer-confirmed-error" message={errors.confirmed} />
              </div>
            </fieldset>
          </div>
        )}

        {step === 2 && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Review</h2>
            <dl className="grid gap-3 sm:grid-cols-2 text-sm">
              {[['Customer type', titleCase(form.customerType!)], ['Name', `${form.firstName} ${form.lastName}`],
                ['Email', form.email], ['Phone', form.phone], ['Consent method', titleCase(form.method)],
                ['Authorization valid for', `${form.validDays} days`], ['Consent note', form.consentNote || '—']]
                .map(([label, value]) => (
                  <div key={label} className="rounded-xl bg-ink-100 p-3"><dt className="text-ink-500">{label}</dt><dd className="font-semibold break-words">{value}</dd></div>
                ))}
            </dl>
            <p className="text-sm text-ink-500 mt-4">A temporary password is created and shown once after you register the customer.</p>
          </div>
        )}

        <div className="flex flex-wrap justify-between gap-2 mt-6">
          <button type="button" className="btn btn-ghost" onClick={() => step === 0 ? navigate('/agent/customers') : setStep(step - 1)}>
            {step === 0 ? 'Cancel' : 'Back'}
          </button>
          <button type="submit" className={greenButton} disabled={create.isPending}>
            {step < 2 ? 'Continue' : create.isPending ? 'Registering…' : 'Register customer'}
          </button>
        </div>
      </form>
    </>
  );
}
