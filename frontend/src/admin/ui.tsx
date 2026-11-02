import { clsx } from 'clsx';
import { Search } from 'lucide-react';
import { useDeferredValue, useState, type ReactNode } from 'react';
import { titleCase } from '../agent/ui';

export {
  Avatar, EmptyState, ErrorState, LoadingState, PageHeader, Pagination, errorMessage, money, relativeTime,
  shortDate, titleCase,
} from '../agent/ui';

export const purpleButton = 'btn bg-violet-700 text-white hover:bg-violet-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-700 disabled:opacity-60';
export const purpleOutline = 'btn bg-white text-violet-800 ring-1 ring-violet-700/40 hover:bg-violet-50 disabled:opacity-60';

const tones: Record<string, string> = {
  VERIFIED: 'badge-green', PENDING: 'badge-amber', SUSPENDED: 'badge-red',
  REQUESTED: 'badge-amber', AWAITING_REVIEW: 'badge-amber', DRAFT: 'badge-gray', MATCHED: 'badge-blue',
  PAID_ESCROW: 'badge-blue', PICKED_UP: 'badge-violet', IN_TRANSIT: 'badge-violet', ARRIVED: 'badge-blue', DELIVERED: 'badge-green',
  COMPLETED: 'badge-green', CANCELLED: 'badge-red', DISPUTED: 'badge-red', PAID: 'badge-green',
  UPCOMING: 'badge-blue', IN_PROGRESS: 'badge-violet', DEMO_HELD: 'badge-amber', DEMO_RELEASED: 'badge-green',
  DEMO_REFUNDED: 'badge-gray', NONE: 'badge-gray',
  STRIPE_TEST_HELD: 'badge-amber', STRIPE_TEST_RELEASED: 'badge-green', STRIPE_TEST_REFUNDED: 'badge-gray',
  SUCCEEDED: 'badge-green', REFUNDED: 'badge-gray',
  ADMIN: 'badge-gray', AGENT: 'badge-amber', SENDER: 'badge-blue', TRAVELER: 'badge-violet',
};
const labels: Record<string, string> = {
  PAID_ESCROW: 'Paid (escrow)', DEMO_HELD: 'Held', DEMO_RELEASED: 'Released', DEMO_REFUNDED: 'Refunded',
  AWAITING_REVIEW: 'Awaiting review',
  STRIPE_TEST_HELD: 'Stripe test paid', STRIPE_TEST_RELEASED: 'Test completed (no payout)', STRIPE_TEST_REFUNDED: 'Stripe test refunded',
};

export function AdminBadge({ status, label }: { status: string; label?: string }) {
  return <span className={clsx('badge', tones[status] ?? 'badge-gray')}>{label ?? labels[status] ?? titleCase(status)}</span>;
}

export function KpiCard({ label, value, icon, tone, hint }: {
  label: string; value: ReactNode; icon: ReactNode; tone: 'violet' | 'emerald' | 'sky' | 'amber'; hint?: string;
}) {
  const toneClass = { violet: 'bg-violet-100 text-violet-700', emerald: 'bg-emerald-100 text-emerald-700',
    sky: 'bg-sky-100 text-sky-700', amber: 'bg-amber-100 text-amber-700' }[tone];
  return (
    <div className="card flex items-start justify-between gap-3">
      <div className="min-w-0">
        <div className="text-sm text-ink-500">{label}</div>
        <div className="mt-1 text-2xl font-bold text-ink-900">{value}</div>
        {hint && <div className="mt-1 text-xs text-ink-500">{hint}</div>}
      </div>
      <span className={clsx('w-11 h-11 rounded-xl grid place-items-center shrink-0', toneClass)} aria-hidden="true">{icon}</span>
    </div>
  );
}

export interface TabItem { value: string; label: string; count?: number }

/** Tab strip using the ARIA tabs pattern with arrow-key navigation. */
export function Tabs({ tabs, value, onChange, label }: {
  tabs: TabItem[]; value: string; onChange: (value: string) => void; label: string;
}) {
  const move = (index: number) => {
    const next = tabs[(index + tabs.length) % tabs.length];
    onChange(next.value);
    requestAnimationFrame(() => document.getElementById(`tab-${label}-${next.value}`)?.focus());
  };
  return (
    <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto border-b border-ink-300/60">
      {tabs.map((tab, index) => {
        const selected = tab.value === value;
        return (
          <button key={tab.value} id={`tab-${label}-${tab.value}`} type="button" role="tab" aria-selected={selected}
            tabIndex={selected ? 0 : -1} onClick={() => onChange(tab.value)}
            onKeyDown={e => {
              if (e.key === 'ArrowRight') { e.preventDefault(); move(index + 1); }
              if (e.key === 'ArrowLeft') { e.preventDefault(); move(index - 1); }
            }}
            className={clsx('-mb-px whitespace-nowrap border-b-2 px-4 py-2 text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-violet-600',
              selected ? 'border-violet-700 text-violet-800' : 'border-transparent text-ink-500 hover:text-ink-800')}>
            {tab.label}
            {tab.count !== undefined && (
              <span className={clsx('ml-2 rounded-full px-2 py-0.5 text-xs', selected ? 'bg-violet-100 text-violet-800' : 'bg-ink-100 text-ink-500')}>
                {tab.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

export function SearchInput({ value, onChange, label, placeholder }: {
  value: string; onChange: (value: string) => void; label: string; placeholder?: string;
}) {
  return (
    <label className="relative block w-full sm:w-72">
      <span className="sr-only">{label}</span>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-500" aria-hidden="true" />
      <input type="search" className="input pl-9" value={value} placeholder={placeholder ?? label}
        onChange={e => onChange(e.target.value)} />
    </label>
  );
}

export function FilterSelect({ value, onChange, label, options }: {
  value: string; onChange: (value: string) => void; label: string; options: { value: string; label: string }[];
}) {
  return (
    <label className="block w-full sm:w-48">
      <span className="sr-only">{label}</span>
      <select className="input" value={value} onChange={e => onChange(e.target.value)} aria-label={label}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </label>
  );
}

/** Shared list state: search text (deferred for querying), status filter and page; filters reset the page. */
export function useListState(initialStatus = '') {
  const [q, setQ] = useState('');
  const [status, setStatusRaw] = useState(initialStatus);
  const [page, setPage] = useState(0);
  const deferredQ = useDeferredValue(q.trim());
  return {
    q, status, page, deferredQ, setPage,
    setQ: (value: string) => { setQ(value); setPage(0); },
    setStatus: (value: string) => { setStatusRaw(value); setPage(0); },
    query: { q: deferredQ, status, page },
  };
}

export function TableCard({ children, footer }: { children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="card p-0 overflow-hidden">
      <div className="overflow-x-auto">{children}</div>
      {footer && <div className="px-5 pb-4">{footer}</div>}
    </div>
  );
}

export const th = 'px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-ink-500 bg-ink-100/60';
export const td = 'px-5 py-3 text-sm text-ink-800 align-middle';
