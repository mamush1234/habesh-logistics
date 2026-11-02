import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { AdminConsoleApi } from '../api';
import type { FeePayer, SettingsInput } from '../types';
import { ErrorState, LoadingState, PageHeader, errorMessage, purpleButton, relativeTime } from '../ui';

type Errors = Partial<Record<keyof SettingsInput, string>>;

const payers: { value: FeePayer; label: string; hint: string }[] = [
  { value: 'SENDER', label: 'Sender', hint: 'Fee is added on top of the delivery price.' },
  { value: 'TRAVELER', label: 'Traveler', hint: 'Fee is deducted from the traveler payout.' },
  { value: 'SPLIT', label: 'Split 50/50', hint: 'Each side covers half of the fee.' },
];

function validatePercent(raw: string, name: string) {
  if (raw.trim() === '') return `${name} is required`;
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 0 || n > 100) return `${name} must be between 0 and 100`;
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(raw.trim())) return 'Use at most two decimal places';
  return undefined;
}

export function AdminSettings() {
  const qc = useQueryClient();
  const settings = useQuery({ queryKey: ['admin-console', 'settings'], queryFn: AdminConsoleApi.settings });
  const [fee, setFee] = useState('');
  const [commission, setCommission] = useState('');
  const [payer, setPayer] = useState<FeePayer>('SENDER');
  const [errors, setErrors] = useState<Errors>({});

  useEffect(() => {
    if (!settings.data) return;
    setFee(String(settings.data.platformFeePercent));
    setCommission(String(settings.data.agentCommissionPercent));
    setPayer(settings.data.feePayer);
  }, [settings.data]);

  const save = useMutation({
    mutationFn: AdminConsoleApi.updateSettings,
    onSuccess: data => {
      qc.setQueryData(['admin-console', 'settings'], data);
      qc.invalidateQueries({ queryKey: ['admin-console'] });
      toast.success('Platform settings saved');
    },
    onError: error => toast.error(errorMessage(error)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const next: Errors = {
      platformFeePercent: validatePercent(fee, 'Platform fee'),
      agentCommissionPercent: validatePercent(commission, 'Agent commission'),
    };
    setErrors(next);
    if (next.platformFeePercent || next.agentCommissionPercent) return;
    save.mutate({ platformFeePercent: Number(fee), agentCommissionPercent: Number(commission), feePayer: payer });
  };

  return (
    <>
      <PageHeader title="Settings" subtitle="Platform-wide pricing used for fees, payouts and default agent commissions." />
      {settings.isLoading && <LoadingState label="Loading settings…" />}
      {settings.isError && <ErrorState error={settings.error} onRetry={() => settings.refetch()} />}
      {settings.data && (
        <form onSubmit={submit} noValidate className="card max-w-2xl space-y-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <PercentField id="platform-fee" label="Platform fee" value={fee} onChange={setFee} error={errors.platformFeePercent}
              hint="Share of each delivery price kept by the platform." />
            <PercentField id="agent-commission" label="Default agent commission" value={commission} onChange={setCommission}
              error={errors.agentCommissionPercent} hint="Used when an agent has no individual rate." />
          </div>
          <fieldset>
            <legend className="label">Who pays the platform fee?</legend>
            <div className="grid gap-3 sm:grid-cols-3">
              {payers.map(p => (
                <label key={p.value} className={`cursor-pointer rounded-xl p-3 ring-1 ${payer === p.value ? 'bg-violet-50 ring-violet-600' : 'ring-ink-300'}`}>
                  <span className="flex items-center gap-2 font-medium text-ink-900">
                    <input type="radio" name="fee-payer" value={p.value} checked={payer === p.value} onChange={() => setPayer(p.value)} />
                    {p.label}
                  </span>
                  <span className="mt-1 block text-xs text-ink-500">{p.hint}</span>
                </label>
              ))}
            </div>
          </fieldset>
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-ink-300/60 pt-4">
            <p className="text-xs text-ink-500">
              {settings.data.updatedBy
                ? `Last updated ${relativeTime(settings.data.updatedAt)} by ${settings.data.updatedBy}`
                : 'Using default settings'}
            </p>
            <button type="submit" className={purpleButton} disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save settings'}</button>
          </div>
        </form>
      )}
    </>
  );
}

function PercentField({ id, label, value, onChange, error, hint }: {
  id: string; label: string; value: string; onChange: (v: string) => void; error?: string; hint: string;
}) {
  return (
    <div>
      <label htmlFor={id} className="label">{label}</label>
      <div className="relative">
        <input id={id} inputMode="decimal" className="input pr-8" value={value} onChange={e => onChange(e.target.value)}
          aria-invalid={!!error} aria-describedby={`${id}-hint${error ? ` ${id}-error` : ''}`} />
        <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm text-ink-500" aria-hidden="true">%</span>
      </div>
      <p id={`${id}-hint`} className="mt-1 text-xs text-ink-500">{hint}</p>
      {error && <p id={`${id}-error`} role="alert" className="mt-1 text-xs text-rose-700">{error}</p>}
    </div>
  );
}
