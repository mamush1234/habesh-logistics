import type { TrackingEvent } from '../types';
import { Check, Circle } from 'lucide-react';

export function Timeline({ events }: { events: TrackingEvent[] }) {
  if (events.length === 0) return <p className="text-ink-500 text-sm">No events yet.</p>;
  return (
    <ol className="relative border-l-2 border-ink-300/60 ml-3 space-y-5">
      {events.map((e, i) => {
        const done = true; // all shown events already happened
        return (
          <li key={e.id} className="pl-5">
            <span className="absolute -left-[11px] mt-1 w-5 h-5 rounded-full bg-emerald-500 ring-4 ring-white grid place-items-center">
              <Check className="w-3 h-3 text-white" />
            </span>
            <div className="flex items-baseline justify-between gap-4">
              <p className="font-semibold text-ink-900">{e.description || e.eventType}</p>
              <time className="text-xs text-ink-500 shrink-0">{new Date(e.createdAt).toLocaleString()}</time>
            </div>
            {e.location && <p className="text-sm text-ink-500">{e.location}</p>}
          </li>
        );
      })}
    </ol>
  );
}
