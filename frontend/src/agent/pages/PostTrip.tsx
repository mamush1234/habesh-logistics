import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Check, Plane } from 'lucide-react';
import { AgentApi } from '../api';
import { CustomerPicker } from '../CustomerPicker';
import type { AgentCustomer } from '../types';
import { itemTypes } from '../../api/marketplace';
import { initialTripForm, MarketTripFields, tripFormError, tripFormInput, TripDetailsSummary, type TripFormState } from '../../components/MarketTripFields';
import { errorMessage, FieldError, greenButton, PageHeader, shortDate } from '../ui';

export function PostTripPage() {
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const preselect = params.get('customer');
  const [customer, setCustomer] = useState<AgentCustomer | null>(null);
  const [form, setForm] = useState<TripFormState>(() => ({ ...initialTripForm(), allowedItems: itemTypes.filter(t => t === 'DOCUMENTS' || t === 'CLOTHING'),
    blockedItems: itemTypes.filter(t => t !== 'DOCUMENTS' && t !== 'CLOTHING') }));
  const [errors, setErrors] = useState<Record<string, string>>({});

  const preselected = useQuery({ queryKey: ['agent', 'customer', preselect], queryFn: () => AgentApi.customer(preselect!), enabled: !!preselect });
  useEffect(() => {
    const found = preselected.data;
    if (found?.type === 'TRAVELER') setCustomer(c => c ?? found);
  }, [preselected.data]);

  const create = useMutation({
    mutationFn: () => AgentApi.createTrip(customer!.id, tripFormInput(form)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['agent'] }); toast.success('Trip posted'); },
    onError: error => toast.error(errorMessage(error)),
  });

  if (create.data) {
    const trip = create.data;
    return (
      <>
        <PageHeader title="Post Trip" />
        <section className="card max-w-2xl" aria-labelledby="trip-done">
          <h2 id="trip-done" className="text-lg font-semibold text-emerald-800 flex items-center gap-2"><Check className="w-5 h-5" aria-hidden="true" /> Trip posted</h2>
          <p className="text-sm text-ink-500 mt-1">{trip.origin} → {trip.destination} on {shortDate(trip.departureDate)} for {trip.customerName}, {trip.capacityKg} kg capacity.</p>
          <TripDetailsSummary trip={trip} />
          <div className="flex flex-wrap gap-2 mt-4">
            <Link to="/agent/tracking" className={greenButton}>View my trips</Link>
            <button type="button" className="btn btn-ghost" onClick={() => create.reset()}>Post another trip</button>
          </div>
        </section>
      </>
    );
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const e: Record<string, string> = {};
    if (!customer) e.customer = 'Choose a traveler customer';
    else if (customer.type !== 'TRAVELER') e.customer = 'Trips can only be posted for traveler customers';
    else if (!customer.authorization) e.customer = 'This customer has no active authorization. Grant one first.';
    const problem = tripFormError(form);
    if (problem) e.details = problem;
    if (Number(form.capacityKg) > 1000) e.details = 'Enter capacity between 0.1 and 1000 kg';
    setErrors(e);
    const first = Object.keys(e)[0];
    if (first) { document.getElementById(`trip-${first}`)?.focus(); return; }
    create.mutate();
  };

  return (
    <>
      <PageHeader title="Post Trip" subtitle="Publish luggage space for one of your traveler customers." />
      <form className="card max-w-3xl grid gap-4 sm:grid-cols-2" onSubmit={submit}>
        <div className="sm:col-span-2">
          <label className="label" htmlFor="trip-customer">Traveler customer</label>
          <CustomerPicker id="trip-customer" type="TRAVELER" value={customer} onChange={setCustomer} error={errors.customer} />
          {customer && <FieldError id="trip-customer-selected-error" message={errors.customer} />}
        </div>
        <div id="trip-details" tabIndex={-1} className="sm:col-span-2" aria-describedby="trip-details-error">
          <FieldError id="trip-details-error" message={errors.details} />
        </div>
        <MarketTripFields form={form} onChange={setForm} />
        <div className="sm:col-span-2 flex justify-end">
          <button type="submit" className={greenButton} disabled={create.isPending}>
            <Plane className="w-4 h-4" aria-hidden="true" /> {create.isPending ? 'Posting…' : 'Post trip'}
          </button>
        </div>
      </form>
    </>
  );
}
