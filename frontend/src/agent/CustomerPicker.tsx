import { useDeferredValue, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { Search } from 'lucide-react';
import { AgentApi } from './api';
import type { AgentCustomer, CustomerType } from './types';
import { Avatar, FieldError, StatusBadge } from './ui';

export function CustomerPicker({ id, type, value, onChange, error }: {
  id: string; type?: CustomerType; value: AgentCustomer | null; onChange: (customer: AgentCustomer | null) => void; error?: string;
}) {
  const [search, setSearch] = useState('');
  const q = useDeferredValue(search.trim());
  const query = useQuery({
    queryKey: ['agent', 'customers', type, q, 0],
    queryFn: () => AgentApi.customers({ type, q, page: 0 }),
    enabled: !value,
  });

  if (value) {
    return (
      <div className="flex items-center gap-3 rounded-xl ring-1 ring-emerald-600 bg-emerald-50/50 p-3">
        <Avatar name={value.name} size="sm" />
        <div className="min-w-0 flex-1">
          <div className="font-semibold truncate">{value.name}</div>
          <div className="text-xs text-ink-500 truncate">{value.email}{value.phone ? ` · ${value.phone}` : ''}</div>
        </div>
        {!value.authorization && <StatusBadge status="EXPIRED" label="No authorization" />}
        <button type="button" className="btn btn-ghost !py-1 !px-3 text-xs" onClick={() => onChange(null)}>Change</button>
      </div>
    );
  }

  return (
    <div>
      <div className="relative">
        <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink-500" aria-hidden="true" />
        <input id={id} className="input !pl-9" type="search" value={search} onChange={e => setSearch(e.target.value)}
          placeholder="Search your customers by name, email or phone" aria-invalid={!!error} aria-describedby={`${id}-error`}
          autoComplete="off" />
      </div>
      <FieldError id={`${id}-error`} message={error} />
      <div className="mt-2 max-h-64 overflow-y-auto rounded-xl ring-1 ring-ink-300/60 bg-white" role="listbox" aria-label="Matching customers">
        {query.isPending && <p role="status" className="p-3 text-sm text-ink-500">Searching…</p>}
        {query.isError && <p role="alert" className="p-3 text-sm text-rose-700">Could not load customers. <button type="button" className="underline" onClick={() => query.refetch()}>Retry</button></p>}
        {query.data?.items.length === 0 && <p className="p-3 text-sm text-ink-500">No matching {type ? type.toLowerCase() : 'customer'}s. Register one first.</p>}
        {query.data?.items.map(c => (
          <button key={c.id} type="button" role="option" aria-selected={false} onClick={() => onChange(c)}
            className={clsx('w-full flex items-center gap-3 p-3 text-left hover:bg-emerald-50 outline-none focus-visible:bg-emerald-50 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-emerald-600')}>
            <Avatar name={c.name} size="sm" />
            <span className="min-w-0 flex-1">
              <span className="block font-semibold truncate">{c.name}</span>
              <span className="block text-xs text-ink-500 truncate">{c.email}</span>
            </span>
            <StatusBadge status={c.type} />
            {!c.authorization && <StatusBadge status="EXPIRED" label="No authorization" />}
          </button>
        ))}
      </div>
    </div>
  );
}
