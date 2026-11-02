import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ShieldCheck, ShieldOff } from 'lucide-react';
import { AgentApi } from '../api';
import type { AgentAuthorization, AgentCustomer, AuthorizationMethod } from '../types';
import { CustomerPicker } from '../CustomerPicker';
import {
  Avatar, EmptyState, ErrorState, errorMessage, FieldError, greenButton, LoadingState, PageHeader, shortDate, StatusBadge,
  titleCase,
} from '../ui';

export const authorizationMethods: AuthorizationMethod[] = ['IN_PERSON', 'PHONE', 'WRITTEN', 'DIGITAL'];

export function useRevokeAuthorization() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: AgentApi.revokeAuthorization,
    onSuccess: () => { toast.success('Authorization revoked'); qc.invalidateQueries({ queryKey: ['agent'] }); },
    onError: error => toast.error(errorMessage(error)),
  });
}

export function GrantAuthorizationForm({ customerId, onDone }: { customerId?: string; onDone?: () => void }) {
  const qc = useQueryClient();
  const [picked, setPicked] = useState<AgentCustomer | null>(null);
  const selected = customerId ?? picked?.id ?? '';
  const [method, setMethod] = useState<AuthorizationMethod>('IN_PERSON');
  const [validDays, setValidDays] = useState(90);
  const [consentNote, setConsentNote] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const grant = useMutation({
    mutationFn: AgentApi.grantAuthorization,
    onSuccess: () => {
      toast.success('Authorization granted');
      qc.invalidateQueries({ queryKey: ['agent'] });
      setConfirmed(false); setConsentNote(''); setPicked(null);
      onDone?.();
    },
    onError: error => toast.error(errorMessage(error)),
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!selected) next.customer = 'Choose a customer';
    if (!Number.isInteger(validDays) || validDays < 1 || validDays > 365) next.validDays = 'Enter 1 to 365 days';
    if (!confirmed) next.confirmed = 'Confirm the customer gave you permission';
    setErrors(next);
    if (Object.keys(next).length) return;
    grant.mutate({ customerId: selected, method, validDays, consentNote: consentNote.trim() || undefined });
  };

  return (
    <form onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2" aria-label="Grant authorization">
      {!customerId && (
        <div className="sm:col-span-2">
          <label className="label" htmlFor="grant-customer">Customer</label>
          <CustomerPicker id="grant-customer" value={picked} onChange={setPicked} error={errors.customer} />
        </div>
      )}
      <div>
        <label className="label" htmlFor="grant-method">How was consent given?</label>
        <select id="grant-method" className="input" value={method} onChange={e => setMethod(e.target.value as AuthorizationMethod)}>
          {authorizationMethods.map(m => <option key={m} value={m}>{titleCase(m)}</option>)}
        </select>
      </div>
      <div>
        <label className="label" htmlFor="grant-days">Valid for (days)</label>
        <input id="grant-days" className="input" type="number" min={1} max={365} value={validDays}
          onChange={e => setValidDays(Number(e.target.value))} aria-invalid={!!errors.validDays} aria-describedby="grant-days-error" />
        <FieldError id="grant-days-error" message={errors.validDays} />
      </div>
      <div className="sm:col-span-2">
        <label className="label" htmlFor="grant-note">Consent note (optional)</label>
        <input id="grant-note" className="input" maxLength={500} value={consentNote} onChange={e => setConsentNote(e.target.value)}
          placeholder="e.g. Signed form on file" />
      </div>
      <div className="sm:col-span-2">
        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" className="mt-1 accent-emerald-700" checked={confirmed} onChange={e => setConfirmed(e.target.checked)}
            aria-invalid={!!errors.confirmed} aria-describedby="grant-confirm-error" />
          The customer has authorized me to create packages and trips on their behalf.
        </label>
        <FieldError id="grant-confirm-error" message={errors.confirmed} />
      </div>
      <div className="sm:col-span-2">
        <button type="submit" className={greenButton} disabled={grant.isPending}>
          <ShieldCheck className="w-4 h-4" aria-hidden="true" /> {grant.isPending ? 'Granting…' : 'Grant authorization'}
        </button>
      </div>
    </form>
  );
}

function AuthorizationRow({ a }: { a: AgentAuthorization }) {
  const revoke = useRevokeAuthorization();
  return (
    <tr className="border-b border-ink-100 last:border-0">
      <td className="py-3 pr-3"><div className="flex items-center gap-3"><Avatar name={a.customerName} size="sm" /><span className="font-semibold">{a.customerName}</span></div></td>
      <td className="py-3 pr-3"><StatusBadge status={a.status} /></td>
      <td className="py-3 pr-3 hidden md:table-cell">{titleCase(a.method)}</td>
      <td className="py-3 pr-3 hidden sm:table-cell">{shortDate(a.grantedAt)}</td>
      <td className="py-3 pr-3">{a.status === 'REVOKED' ? `Revoked ${shortDate(a.revokedAt)}` : shortDate(a.expiresAt)}</td>
      <td className="py-3 text-right">
        {a.status === 'ACTIVE' && (
          <button type="button" className="btn btn-ghost !py-1 !px-3 text-xs !text-rose-700" disabled={revoke.isPending}
            onClick={() => { if (window.confirm(`Revoke authorization for ${a.customerName}?`)) revoke.mutate(a.id); }}
            aria-label={`Revoke authorization for ${a.customerName}`}>
            <ShieldOff className="w-3.5 h-3.5" aria-hidden="true" /> Revoke
          </button>
        )}
      </td>
    </tr>
  );
}

export function AuthorizationsPage() {
  const query = useQuery({ queryKey: ['agent', 'authorizations'], queryFn: AgentApi.authorizations });
  return (
    <>
      <PageHeader title="Authorizations" subtitle="Every on-behalf action needs an active, unexpired customer authorization." />
      <section className="card mb-6" aria-labelledby="grant-heading">
        <h2 id="grant-heading" className="text-lg font-semibold mb-4">Grant or renew</h2>
        <GrantAuthorizationForm />
      </section>
      <section className="card" aria-labelledby="authorization-list">
        <h2 id="authorization-list" className="text-lg font-semibold mb-2">History</h2>
        {query.isPending ? <LoadingState label="Loading authorizations…" />
          : query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} />
          : query.data.length === 0 ? <EmptyState title="No authorizations yet">Authorizations are recorded when you register a customer.</EmptyState>
          : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-ink-500 border-b border-ink-100">
                    <th scope="col" className="py-2 pr-3 font-medium">Customer</th>
                    <th scope="col" className="py-2 pr-3 font-medium">Status</th>
                    <th scope="col" className="py-2 pr-3 font-medium hidden md:table-cell">Method</th>
                    <th scope="col" className="py-2 pr-3 font-medium hidden sm:table-cell">Granted</th>
                    <th scope="col" className="py-2 pr-3 font-medium">Expires</th>
                    <th scope="col" className="py-2 font-medium"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>{query.data.map(a => <AuthorizationRow key={a.id} a={a} />)}</tbody>
              </table>
            </div>
          )}
      </section>
    </>
  );
}
