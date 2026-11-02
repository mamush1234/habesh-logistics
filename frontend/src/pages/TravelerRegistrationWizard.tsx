import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthContext';
import { RegistrationAccountFields, type RegistrationAccount } from '../components/RegistrationAccountFields';
import { initialTripForm, MarketTripFields, tripFormError, tripFormInput, TripDetailsSummary } from '../components/MarketTripFields';
import { Stepper, errorMessage } from '../agent/ui';

function VerificationChecklist() {
  return <section aria-labelledby="traveler-verification" className="rounded-xl bg-amber-50 p-4 text-sm space-y-3 sm:col-span-2">
    <h2 id="traveler-verification" className="font-bold text-amber-900">Verification checklist</h2>
    <ul className="space-y-2">
      <li>Identity document upload <span className="font-semibold text-amber-900">&mdash; Pending setup</span></li>
      <li>Selfie verification <span className="font-semibold text-amber-900">&mdash; Pending setup</span></li>
      <li>Phone verification <span className="font-semibold text-amber-900">&mdash; Pending setup</span></li>
    </ul>
    <p>These services are not available yet. No identity documents or selfies are collected here, and entering your phone number does not verify it.
      Your account starts unverified; only an administrator can update verification status.</p>
  </section>;
}

export function TravelerRegistrationWizard({ account, onChange, next }: {
  account: RegistrationAccount; onChange: (account: RegistrationAccount) => void; next: string;
}) {
  const { registerTraveler } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [trip, setTrip] = useState(initialTripForm);
  const [termsAccepted, setTermsAccepted] = useState(false);
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
      const problem = tripFormError(trip);
      if (problem) { setError(problem); return; }
      if (!termsAccepted) { setError('Accept the traveler community terms before continuing.'); return; }
    }
    setStep(current => current + 1);
  }

  async function submit() {
    setSaving(true);
    setError(null);
    try {
      await registerTraveler({ ...account, tripDetails: tripFormInput(trip), termsAccepted });
      toast.success('Account created and trip posted. Verification is still pending.');
      navigate(next === '/dashboard' || next === '/traveler' || next.startsWith('/traveler?')
        ? '/traveler' : next, { replace: true });
    } catch (cause: unknown) {
      setError(errorMessage(cause, 'Could not create your account and trip. Please retry.'));
    } finally {
      setSaving(false);
    }
  }

  return <div className="mt-6">
    <Stepper steps={['Account', 'Trip Details', 'Review']} current={step} />
    {error && <p role="alert" className="mb-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
    {step < 2 ? <form className="grid sm:grid-cols-2 gap-4" onSubmit={event => { event.preventDefault(); continueStep(); }}>
      {step === 0 ? <RegistrationAccountFields account={account} onChange={onChange} prefix="traveler" /> : <>
        <MarketTripFields form={trip} onChange={setTrip} requireArrival />
        <VerificationChecklist />
        <section aria-labelledby="traveler-terms" className="sm:col-span-2 rounded-xl border border-ink-300 p-4 space-y-3 text-sm">
          <h2 id="traveler-terms" className="font-bold">Traveler community terms</h2>
          <ul className="list-disc pl-5 space-y-1">
            <li>Provide accurate trip and contact details, and only offer capacity you have available.</li>
            <li>Follow airline and customs rules. Do not carry prohibited items or packages whose contents you cannot confirm.</li>
            <li>Coordinate safely with the sender and our team. Do not claim to be verified before administrator approval.</li>
          </ul>
          <label className="flex items-start gap-3">
            <input type="checkbox" required checked={termsAccepted} className="mt-1 accent-emerald-700"
              onChange={event => setTermsAccepted(event.target.checked)} />
            <span>I accept the traveler community terms shown above.</span>
          </label>
        </section>
        <p className="text-sm text-ink-500 sm:col-span-2">This step posts your route, dates, available capacity, and category rules.
          Set a price for each accepted category. Traveler package management remains read-only.</p>
      </>}
      <div className="sm:col-span-2 flex justify-between gap-3">
        {step > 0 ? <button type="button" className="btn btn-ghost" onClick={() => setStep(step - 1)}>Back</button> : <span />}
        <button type="submit" className={button}>Continue to {step === 0 ? 'Trip Details' : 'Review'}</button>
      </div>
    </form> : <div className="space-y-5">
      <section aria-labelledby="review-traveler-account">
        <h2 id="review-traveler-account" className="text-lg font-bold">Account</h2>
        <p>{account.firstName} {account.lastName}</p><p className="break-words">{account.email}</p><p>{account.phone}</p>
        <p className="text-sm text-ink-500">Password is set and will not be displayed.</p>
        <button type="button" disabled={saving} className="btn btn-ghost mt-2" onClick={() => setStep(0)}>Edit account</button>
      </section>
      <section aria-labelledby="review-traveler-trip">
        <h2 id="review-traveler-trip" className="text-lg font-bold">Trip</h2>
        <p>{trip.origin} &rarr; {trip.destination}</p>
        <p>Departs {trip.departureDate} &middot; Arrives {trip.expectedArrivalDate}</p>
        <p>Available capacity: {Number(trip.capacityKg).toFixed(2)} kg</p>
        <p>Will carry: {trip.allowedItems.map(type => type.replaceAll('_', ' ')).join(', ')}</p>
        <TripDetailsSummary trip={tripFormInput(trip)} />
        {trip.blockedItems.length > 0 && <p>Will not carry: {trip.blockedItems.map(type => type.replaceAll('_', ' ')).join(', ')}</p>}
        <button type="button" disabled={saving} className="btn btn-ghost mt-2" onClick={() => setStep(1)}>Edit trip</button>
      </section>
      <VerificationChecklist />
      <p className="text-sm">Traveler community terms accepted. Your acceptance will be saved with your account when you submit.</p>
      <p className="text-sm text-ink-500">Submitting creates your traveler account and posts this trip together. No payment is collected.
        Your trip can appear in traveler search, but your account is not marked verified.</p>
      <div className="flex justify-between gap-3">
        <button type="button" disabled={saving} className="btn btn-ghost" onClick={() => setStep(1)}>Back</button>
        <button type="button" disabled={saving} className={button} onClick={submit}>
          {saving ? 'Creating account and trip...' : 'Create account and post trip'}
        </button>
      </div>
    </div>}
  </div>;
}
