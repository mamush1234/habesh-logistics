import { useDeferredValue, useState, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { Search, UserPlus } from 'lucide-react';
import { AgentApi } from '../api';
import type { CustomerType } from '../types';
import {
  Avatar, EmptyState, ErrorState, greenButton, LoadingState, PageHeader, Pagination, shortDate, StatusBadge,
} from '../ui';

const tabs: { label: string; type?: CustomerType }[] = [
  { label: 'All' }, { label: 'Senders', type: 'SENDER' }, { label: 'Travelers', type: 'TRAVELER' },
];

export function CustomersPage() {
  const [tab, setTab] = useState(0);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const q = useDeferredValue(search.trim());
  const type = tabs[tab].type;
  const query = useQuery({
    queryKey: ['agent', 'customers', type, q, page],
    queryFn: () => AgentApi.customers({ type, q, page }),
    placeholderData: keepPreviousData,
  });

  const selectTab = (index: number) => { setTab(index); setPage(0); };
  const onTabKey = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    event.preventDefault();
    const next = (tab + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length;
    selectTab(next);
    document.getElementById(`customer-tab-${next}`)?.focus();
  };

  return (
    <>
      <PageHeader title="My Customers" subtitle="Senders and travelers you manage on their behalf"
        actions={<Link to="/agent/customers/new" className={greenButton}><UserPlus className="w-4 h-4" aria-hidden="true" /> Add Customer</Link>} />

      <section className="card">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div role="tablist" aria-label="Customer type" className="inline-flex rounded-full bg-ink-100 p-1">
            {tabs.map((t, index) => (
              <button key={t.label} id={`customer-tab-${index}`} type="button" role="tab" aria-selected={tab === index}
                aria-controls="customer-panel" tabIndex={tab === index ? 0 : -1} onKeyDown={onTabKey}
                onClick={() => selectTab(index)}
                className={clsx('px-4 py-1.5 rounded-full text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-emerald-600',
                  tab === index ? 'bg-emerald-700 text-white' : 'text-ink-800 hover:bg-white')}>
                {t.label}
              </button>
            ))}
          </div>
          <label className="relative w-full sm:w-72">
            <span className="sr-only">Search customers</span>
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink-500" aria-hidden="true" />
            <input className="input !pl-9" type="search" placeholder="Search name, email or phone" value={search}
              onChange={e => { setSearch(e.target.value); setPage(0); }} />
          </label>
        </div>

        <div id="customer-panel" role="tabpanel" aria-labelledby={`customer-tab-${tab}`}>
          {query.isPending ? <LoadingState label="Loading customers…" />
            : query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} />
            : query.data.items.length === 0 ? (
              <EmptyState title={q ? 'No customers match your search' : 'No customers yet'}
                action={!q && <Link to="/agent/customers/new" className={greenButton}>Add Customer</Link>}>
                {q ? 'Try a different name, email or phone number.' : 'Register a sender or traveler to manage their shipments.'}
              </EmptyState>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-left text-ink-500 border-b border-ink-100">
                        <th scope="col" className="py-2 pr-3 font-medium">Customer</th>
                        <th scope="col" className="py-2 pr-3 font-medium">Type</th>
                        <th scope="col" className="py-2 pr-3 font-medium hidden md:table-cell">Phone</th>
                        <th scope="col" className="py-2 pr-3 font-medium">Shipments / Trips</th>
                        <th scope="col" className="py-2 pr-3 font-medium hidden md:table-cell">Authorization</th>
                        <th scope="col" className="py-2 font-medium"><span className="sr-only">Actions</span></th>
                      </tr>
                    </thead>
                    <tbody className={clsx(query.isPlaceholderData && 'opacity-60')}>
                      {query.data.items.map(c => (
                        <tr key={c.id} className="border-b border-ink-100 last:border-0">
                          <td className="py-3 pr-3">
                            <div className="flex items-center gap-3">
                              <Avatar name={c.name} size="sm" />
                              <div className="min-w-0">
                                <div className="font-semibold text-ink-900 truncate">{c.name}</div>
                                <div className="text-xs text-ink-500 truncate">{c.email}</div>
                              </div>
                            </div>
                          </td>
                          <td className="py-3 pr-3"><StatusBadge status={c.type} label={c.type === 'SENDER' ? 'Sender' : 'Traveler'} /></td>
                          <td className="py-3 pr-3 hidden md:table-cell">{c.phone ?? '—'}</td>
                          <td className="py-3 pr-3">
                            {c.type === 'SENDER' ? `${c.shipmentCount} shipment${c.shipmentCount === 1 ? '' : 's'}`
                              : `${c.tripCount} trip${c.tripCount === 1 ? '' : 's'}`}
                          </td>
                          <td className="py-3 pr-3 hidden md:table-cell">
                            {c.authorization
                              ? <span className="text-xs"><StatusBadge status="ACTIVE" /> <span className="text-ink-500">until {shortDate(c.authorization.expiresAt)}</span></span>
                              : <StatusBadge status="EXPIRED" label="None" />}
                          </td>
                          <td className="py-3 text-right">
                            <Link to={`/agent/customers/${c.id}`} className="btn btn-ghost !py-1 !px-3 text-xs" aria-label={`View ${c.name}`}>View</Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={setPage} />
              </>
            )}
        </div>
      </section>
    </>
  );
}
