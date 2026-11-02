import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';

export type DateRange = { from: string; to: string };
export type RangePreset = '7d' | '30d' | '90d' | 'ytd' | 'custom';

const iso = (date: Date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
};

export function presetRange(preset: Exclude<RangePreset, 'custom'>, today = new Date()): DateRange {
  const to = iso(today);
  if (preset === 'ytd') return { from: `${today.getFullYear()}-01-01`, to };
  const days = preset === '7d' ? 6 : preset === '30d' ? 29 : 89;
  const from = new Date(today);
  from.setDate(from.getDate() - days);
  return { from: iso(from), to };
}

type DateRangeState = {
  range: DateRange;
  preset: RangePreset;
  setPreset: (preset: Exclude<RangePreset, 'custom'>) => void;
  setCustom: (range: DateRange) => void;
};

const DateRangeContext = createContext<DateRangeState | null>(null);

export function DateRangeProvider({ children }: { children: ReactNode }) {
  const [preset, setPresetState] = useState<RangePreset>('30d');
  const [range, setRange] = useState<DateRange>(() => presetRange('30d'));
  const value = useMemo<DateRangeState>(() => ({
    range,
    preset,
    setPreset: next => { setPresetState(next); setRange(presetRange(next)); },
    setCustom: next => { setPresetState('custom'); setRange(next); },
  }), [range, preset]);
  return <DateRangeContext.Provider value={value}>{children}</DateRangeContext.Provider>;
}

export function useDateRange() {
  const value = useContext(DateRangeContext);
  if (!value) throw new Error('useDateRange must be used inside DateRangeProvider');
  return value;
}
