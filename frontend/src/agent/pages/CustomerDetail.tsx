import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { ArrowLeft, Mail, PackagePlus, Phone, Plane, ShieldCheck, ShieldOff } from 'lucide-react';
import { AgentApi } from '../api';
import { GrantAuthorizationForm, useRevokeAuthorization } from './Authorizations';
import {
  Avatar, EmptyState, ErrorState, greenButton, LoadingState, outlineButton, shortDate, StatusBadge, titleCase,
} from '../ui';

export function CustomerDetailPage() {
  const { id = '' } = useParams();
  const [granting, setGranting] = useState(false);
  const revoke = useRevokeAuthorization();
  const query = useQuery({ queryKey: ['agent', 'customer', id], queryFn: () => AgentApi.customer(id), retry: false });

  if (query.isPending) return <LoadingState label="Loading customer…" />;
  if (query.isError) {
    if (isAxiosError(query.error) && [403, 404].includes(query.error.response?.status ?? 0))
      return <EmptyState title="Customer not found" action={<Link to="/agent/customers" className={greenButton}>Back to customers</Link>}>
        This customer does not exist or is not managed by you.
      </EmptyState>;
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  const c = query.data;
  const auth = c.authorization;

  return (
    <>
      <Link to="/agent/customers" className="inline-flex items-center gap-1 text-sm text-emerald-700 hover:underline mb-4">
        <ArrowLeft className="w-4 h-4" aria-hidden="true" /> My Customers
      </Link>
      <section className="card flex flex-wrap items-center gap-4 mb-6">
        <Avatar name={c.name} size="lg" />
        <div className="min-w-0 flex-1">
          <h1 className="text-2xl font-bold text-ink-900">{c.name}</h1>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-500 mt-1">
            <span className="inline-flex items-center gap-1"><Mail className="w-4 h-4" aria-hidden="true" /> {c.email}</span>
            {c.phone && <span className="inline-flex items-center gap-1"><Phone className="w-4 h-4" aria-hidden="true" /> {c.phone}</span>}
            <span>Customer since {shortDate(c.createdAt)}</span>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={c.type} />
          {c.mustChangePassword && <StatusBadge status="PENDING" label="Has not signed in yet" />}
          {!c.active && <StatusBadge status="CANCELLED" label="Inactive" />}
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="card" aria-labelledby="customer-activity">
          <h2 id="customer-activity" className="text-lg font-semibold mb-3">{c.type === 'SENDER' ? 'Shipments' : 'Trips'}</h2>
          <p className="text-3xl font-bold">{c.type === 'SENDER' ? c.shipmentCount : c.tripCount}</p>
          <p className="text-sm text-ink-500 mb-4">{c.type === 'SENDER' ? 'Packages submitted (drafts excluded)' : 'Trips posted'}</p>
          {c.type === 'SENDER'
            ? <Link to={`/agent/packages/new?customer=${c.id}`} className={greenButton}><PackagePlus className="w-4 h-4" aria-hidden="true" /> Create Package</Link>
            : <Link to={`/agent/trips/new?customer=${c.id}`} className={greenButton}><Plane className="w-4 h-4" aria-hidden="true" /> Post Trip</Link>}
          {!auth && <p className="text-xs text-rose-700 mt-2">An active authorization is required before you can act for this customer.</p>}
        </section>

        <section className="card" aria-labelledby="customer-authorization">
          <h2 id="customer-authorization" className="text-lg font-semibold mb-3">Authorization</h2>
          {auth ? (
            <div className="space-y-2 text-sm">
              <div className="flex items-center gap-2"><StatusBadge status={auth.status} /> {titleCase(auth.method)}</div>
              <p>Granted {shortDate(auth.grantedAt)} · expires {shortDate(auth.expiresAt)}</p>
              {auth.consentNote && <p className="text-ink-500">{auth.consentNote}</p>}
              <div className="flex flex-wrap gap-2 pt-2">
                <button type="button" className={outlineButton} onClick={() => setGranting(!granting)} aria-expanded={granting}>
                  <ShieldCheck className="w-4 h-4" aria-hidden="true" /> Renew
                </button>
                <button type="button" className="btn btn-ghost !text-rose-700" disabled={revoke.isPending}
                  onClick={() => { if (window.confirm(`Revoke authorization for ${c.name}?`)) revoke.mutate(auth.id); }}>
                  <ShieldOff className="w-4 h-4" aria-hidden="true" /> Revoke
                </button>
              </div>
            </div>
          ) : (
            <>
              <p className="text-sm text-ink-500 mb-3">No active authorization.</p>
              {!granting && <button type="button" className={greenButton} onClick={() => setGranting(true)} aria-expanded={false}>
                <ShieldCheck className="w-4 h-4" aria-hidden="true" /> Grant authorization
              </button>}
            </>
          )}
          {granting && <div className="mt-4 border-t border-ink-100 pt-4"><GrantAuthorizationForm customerId={c.id} onDone={() => setGranting(false)} /></div>}
        </section>
      </div>
    </>
  );
}
