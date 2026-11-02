import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthContext';
import { initialRequestForm, MarketRequestFields } from '../components/MarketRequestFields';
import { initialPackageContents, PackageItemsEditor, packageContentsError, packageContentsInput,
  PackageContentsSummary, totalPackageWeight } from '../components/PackageItemsEditor';
import type { RegisterRequest } from '../types';
import { RegistrationAccountFields } from '../components/RegistrationAccountFields';
import { Stepper, errorMessage } from '../agent/ui';

export function SenderRegistrationWizard({ account, onChange, next }: {
  account: Omit<RegisterRequest, 'role'>; onChange: (account: Omit<RegisterRequest, 'role'>) => void; next: string;
}) {
  const { registerSender } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [details, setDetails] = useState(initialRequestForm);
  const [contents, setContents] = useState(initialPackageContents);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const button = 'btn bg-emerald-700 text-white hover:bg-emerald-800';
  function continueStep() {
    setError(null);
    if (step === 0 && new TextEncoder().encode(account.password).length > 72) {
      setError('Password must be at most 72 UTF-8 bytes.');
      return;
    }
    if (step === 1) {
      const problem = packageContentsError(contents);
      if (problem) { setError(problem); return; }
    }
    setStep(current => current + 1);
  }
  async function submit() {
    setSaving(true);
    setError(null);
    try {
      const requestId = await registerSender({ ...account, packageDetails: {
        ...details, ...packageContentsInput(contents), itemType: contents.items[0].category,
        weightKg: totalPackageWeight(contents), offeredPrice: Number(details.offeredPrice),
      } });
      toast.success('Account created and package posted. Our team will review your request.');
      navigate(next === '/dashboard' || next === '/send' || next.startsWith('/send?')
        ? `/marketplace/requests/${requestId}` : next, { replace: true });
    } catch (cause: unknown) {
      setError(errorMessage(cause, 'Could not create your account and package. Please retry.'));
    } finally {
      setSaving(false);
    }
  }
  return <div className="mt-6">
    <Stepper steps={['Account', 'Package Details', 'Review']} current={step} />
    {error && <p role="alert" className="mb-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
    {step < 2 ? <form onSubmit={event => { event.preventDefault(); continueStep(); }} className="grid sm:grid-cols-2 gap-4">
      {step === 0 ? <RegistrationAccountFields account={account} onChange={onChange} prefix="sender" /> : <>
        <MarketRequestFields hideItemType hideWeight form={details} onChange={setDetails} />
        <PackageItemsEditor value={contents} onChange={setContents} />
        <p className="text-sm text-ink-500 sm:col-span-2">Your initial post is reviewed by an administrator before travelers can see it.
          Offered prices are in USD. Payments are currently demo-only. Do not put contact details in the package description.</p>
      </>}
      <div className="sm:col-span-2 flex justify-between gap-3">
        {step > 0 ? <button type="button" className="btn btn-ghost" onClick={() => setStep(step - 1)}>Back</button> : <span />}
        <button className={button} type="submit">Continue to {step === 0 ? 'Package Details' : 'Review'}</button>
      </div>
    </form> : <div className="space-y-5">
      <section aria-labelledby="review-account">
        <h2 id="review-account" className="text-lg font-bold">Account</h2>
        <p>{account.firstName} {account.lastName}</p><p className="break-words">{account.email}</p><p>{account.phone}</p>
        <p className="text-sm text-ink-500">Password is set and will not be displayed.</p>
        <button type="button" disabled={saving} className="btn btn-ghost mt-2" onClick={() => setStep(0)}>Edit account</button>
      </section>
      <section aria-labelledby="review-package">
        <h2 id="review-package" className="text-lg font-bold">Package</h2>
        <p>{details.pickupCity} &rarr; {details.destinationCity}</p>
        <p>Available {details.availableFrom} &middot; Deliver by {details.deadline}</p>
        <p>Total weight: {totalPackageWeight(contents).toFixed(2)} kg
          &middot; Offered ${Number(details.offeredPrice).toFixed(2)}</p>
        <p className="whitespace-pre-wrap break-words">{details.description}</p>
        <PackageContentsSummary {...packageContentsInput(contents)} />
        <button type="button" disabled={saving} className="btn btn-ghost mt-2" onClick={() => setStep(1)}>Edit package</button>
      </section>
      <p className="text-sm text-ink-500">Submitting creates your sender account and posts this package together.
        Our team will review the request and coordinate the next steps. No payment is collected.</p>
      <div className="flex justify-between gap-3">
        <button type="button" disabled={saving} className="btn btn-ghost" onClick={() => setStep(1)}>Back</button>
        <button type="button" disabled={saving} className={button} onClick={submit}>{saving ? 'Creating account and package...' : 'Create account and post package'}</button>
      </div>
    </div>}
  </div>;
}
