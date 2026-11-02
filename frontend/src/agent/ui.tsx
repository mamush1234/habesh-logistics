import { isAxiosError } from 'axios';
import { AlertTriangle, Inbox, Loader2 } from 'lucide-react';
import { clsx } from 'clsx';
import type { ReactNode } from 'react';

export const greenButton = 'btn bg-emerald-700 text-white hover:bg-emerald-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:opacity-60';
export const outlineButton = 'btn bg-white text-emerald-800 ring-1 ring-emerald-700/40 hover:bg-emerald-50 disabled:opacity-60';

export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.') {
  if (isAxiosError(error)) return error.response?.data?.message ?? fallback;
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}

export const money = (value: number | null | undefined) =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value ?? 0));
export const shortDate = (value: string | null | undefined) =>
  value ? new Date(value.length === 10 ? `${value}T00:00:00` : value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '—';
export const relativeTime = (value: string) => {
  const minutes = Math.round((Date.now() - new Date(value).getTime()) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return shortDate(value);
};
export const titleCase = (value: string) =>
  value.toLowerCase().split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');

const badgeTone: Record<string, string> = {
  PENDING: 'badge-amber', REQUESTED: 'badge-amber', DRAFT: 'badge-gray', MATCHED: 'badge-blue',
  PAID_ESCROW: 'badge-blue', PICKED_UP: 'badge-blue', IN_TRANSIT: 'badge-blue', ARRIVED: 'badge-blue', DELIVERED: 'badge-green',
  COMPLETED: 'badge-green', PAID: 'badge-green', ACTIVE: 'badge-green', CANCELLED: 'badge-red',
  REVOKED: 'badge-red', EXPIRED: 'badge-red', DISPUTED: 'badge-red', SENDER: 'badge-blue', TRAVELER: 'badge-violet',
};

export function StatusBadge({ status, label }: { status: string; label?: string }) {
  return <span className={clsx('badge', badgeTone[status] ?? 'badge-gray')}>{label ?? titleCase(status)}</span>;
}

const avatarTones = ['bg-emerald-100 text-emerald-800', 'bg-amber-100 text-amber-800', 'bg-sky-100 text-sky-800',
  'bg-rose-100 text-rose-800', 'bg-violet-100 text-violet-800'];

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0]!.toUpperCase()).join('') || '?';
  const tone = avatarTones[[...name].reduce((sum, ch) => sum + ch.charCodeAt(0), 0) % avatarTones.length];
  return (
    <span aria-hidden="true" className={clsx('inline-grid place-items-center rounded-full font-semibold shrink-0', tone,
      size === 'sm' && 'w-8 h-8 text-xs', size === 'md' && 'w-10 h-10 text-sm', size === 'lg' && 'w-12 h-12 text-base')}>
      {initials}
    </span>
  );
}

export function StatCard({ label, value, icon, tone = 'emerald', hint }: {
  label: string; value: ReactNode; icon: ReactNode; tone?: 'emerald' | 'amber' | 'sky' | 'violet'; hint?: string;
}) {
  const tones = { emerald: 'bg-emerald-50 text-emerald-700', amber: 'bg-amber-50 text-amber-700',
    sky: 'bg-sky-50 text-sky-700', violet: 'bg-violet-50 text-violet-700' };
  return (
    <div className="card flex items-center gap-4">
      <span className={clsx('w-12 h-12 rounded-2xl grid place-items-center shrink-0', tones[tone])} aria-hidden="true">{icon}</span>
      <div className="min-w-0">
        <div className="text-sm text-ink-500">{label}</div>
        <div className="text-2xl font-bold text-ink-900">{value}</div>
        {hint && <div className="text-xs text-ink-500">{hint}</div>}
      </div>
    </div>
  );
}

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-10 text-ink-500">
      <Loader2 className="w-5 h-5 animate-spin" aria-hidden="true" /> {label}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 py-10 text-center">
      <AlertTriangle className="w-8 h-8 text-rose-600" aria-hidden="true" />
      <p className="text-ink-800">{errorMessage(error)}</p>
      {onRetry && <button type="button" className={outlineButton} onClick={onRetry}>Try again</button>}
    </div>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 py-10 text-center">
      <Inbox className="w-8 h-8 text-ink-300" aria-hidden="true" />
      <p className="font-semibold text-ink-800">{title}</p>
      {children && <p className="text-sm text-ink-500 max-w-sm">{children}</p>}
      {action}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3 mb-6">
      <div>
        <h1 className="text-2xl font-bold text-ink-900">{title}</h1>
        {subtitle && <p className="text-sm text-ink-500 mt-1">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function Stepper({ steps, current }: { steps: string[]; current: number }) {
  return (
    <ol className="flex items-center gap-2 sm:gap-4 mb-6" aria-label="Progress">
      {steps.map((step, index) => {
        const done = index < current;
        const active = index === current;
        return (
          <li key={step} className="flex items-center gap-2 min-w-0" aria-current={active ? 'step' : undefined}>
            <span className={clsx('step-dot', done ? 'bg-emerald-700 text-white' : active ? 'bg-emerald-100 text-emerald-800 ring-2 ring-emerald-700' : 'bg-ink-100 text-ink-500')}>
              {index + 1}
            </span>
            <span className={clsx('text-sm truncate', active ? 'font-semibold text-ink-900' : 'text-ink-500', !active && 'hidden sm:inline')}>{step}</span>
            {index < steps.length - 1 && <span className="hidden sm:block w-8 h-px bg-ink-300" aria-hidden="true" />}
          </li>
        );
      })}
    </ol>
  );
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <nav className="flex items-center justify-between gap-3 pt-4" aria-label="Pagination">
      <button type="button" className="btn btn-ghost" disabled={page === 0} onClick={() => onChange(page - 1)}>Previous</button>
      <span className="text-sm text-ink-500">Page {page + 1} of {totalPages}</span>
      <button type="button" className="btn btn-ghost" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
    </nav>
  );
}

export function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) return null;
  return <p id={id} className="text-xs text-rose-700 mt-1" role="alert">{message}</p>;
}
