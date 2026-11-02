import { useId } from 'react';
import type { ChartPoint } from '../types';
import { money, shortDate } from '../ui';

const W = 720, H = 260, PAD = { top: 16, right: 48, bottom: 32, left: 64 };

const niceMax = (value: number) => {
  if (value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  return Math.ceil(value / magnitude) * magnitude;
};

/** Hand-drawn SVG combo chart: revenue line (left axis) over booking bars (right axis). */
export function RevenueChart({ points, bucket }: { points: ChartPoint[]; bucket: 'DAY' | 'WEEK' }) {
  const titleId = useId();
  const plotW = W - PAD.left - PAD.right, plotH = H - PAD.top - PAD.bottom;
  const maxRevenue = niceMax(Math.max(0, ...points.map(p => Number(p.revenue))));
  const maxBookings = niceMax(Math.max(0, ...points.map(p => p.bookings)));
  const step = points.length > 0 ? plotW / points.length : plotW;
  const x = (i: number) => PAD.left + step * i + step / 2;
  const yRevenue = (v: number) => PAD.top + plotH - (v / maxRevenue) * plotH;
  const yBookings = (v: number) => PAD.top + plotH - (v / maxBookings) * plotH;
  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${yRevenue(Number(p.revenue)).toFixed(1)}`).join(' ');
  const labelEvery = Math.max(1, Math.ceil(points.length / 8));
  const barWidth = Math.max(2, Math.min(24, step * 0.6));
  const ticks = [0, 0.25, 0.5, 0.75, 1];

  return (
    <figure>
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto" role="img" aria-labelledby={titleId}>
        <title id={titleId}>
          {`Revenue and bookings per ${bucket === 'WEEK' ? 'week' : 'day'}: total ${money(points.reduce((s, p) => s + Number(p.revenue), 0))}, ${points.reduce((s, p) => s + p.bookings, 0)} bookings`}
        </title>
        {ticks.map(t => {
          const y = PAD.top + plotH - t * plotH;
          return (
            <g key={t}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y} y2={y} stroke="#e7e5f0" />
              <text x={PAD.left - 8} y={y + 4} textAnchor="end" fontSize="11" fill="#6b6880">
                {money(maxRevenue * t).replace('.00', '')}
              </text>
              <text x={W - PAD.right + 8} y={y + 4} fontSize="11" fill="#6b6880">{Math.round(maxBookings * t)}</text>
            </g>
          );
        })}
        {points.map((p, i) => (
          <g key={p.date}>
            <rect x={x(i) - barWidth / 2} y={yBookings(p.bookings)} width={barWidth}
              height={PAD.top + plotH - yBookings(p.bookings)} rx="3" fill="#c4b5fd">
              <title>{`${shortDate(p.date)}: ${p.bookings} bookings, ${money(p.revenue)} revenue`}</title>
            </rect>
            {i % labelEvery === 0 && (
              <text x={x(i)} y={H - 10} textAnchor="middle" fontSize="11" fill="#6b6880">
                {new Date(`${p.date}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
              </text>
            )}
          </g>
        ))}
        {points.length > 0 && <path d={line} fill="none" stroke="#6d28d9" strokeWidth="2.5" strokeLinejoin="round" />}
        {points.map((p, i) => (
          <circle key={p.date} cx={x(i)} cy={yRevenue(Number(p.revenue))} r="3" fill="#6d28d9">
            <title>{`${shortDate(p.date)}: ${money(p.revenue)}`}</title>
          </circle>
        ))}
      </svg>
      <figcaption className="mt-3 flex flex-wrap items-center gap-4 text-xs text-ink-500">
        <span className="inline-flex items-center gap-1.5"><span className="h-0.5 w-4 bg-violet-700" aria-hidden="true" />Revenue (left axis)</span>
        <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-sm bg-violet-300" aria-hidden="true" />Bookings (right axis)</span>
      </figcaption>
    </figure>
  );
}
