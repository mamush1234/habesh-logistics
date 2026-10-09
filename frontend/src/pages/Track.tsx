import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PublicApi } from '../api/endpoints';
import { StatusBadge } from '../components/StatusBadge';
import { Timeline } from '../components/Timeline';
import { Search } from 'lucide-react';

export function Track() {
  const { trackingNumber } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState(trackingNumber || '');
  const [current, setCurrent] = useState(trackingNumber || '');

  useEffect(() => { if (trackingNumber) setCurrent(trackingNumber); }, [trackingNumber]);

  const q = useQuery({
    queryKey: ['track', current],
    queryFn: () => PublicApi.track(current),
    enabled: !!current,
    retry: false,
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const tn = input.trim().toUpperCase();
    if (!tn) return;
    navigate(`/track/${tn}`);
    setCurrent(tn);
  }

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-3xl font-bold">Track your package</h1>
      <p className="text-ink-500 mt-1">Enter your HL tracking number (e.g. <code className="bg-ink-100 px-1.5 py-0.5 rounded">HL-000101</code>).</p>
      <form onSubmit={submit} className="mt-5 flex gap-2">
        <input className="input" placeholder="HL-000000" value={input} onChange={e => setInput(e.target.value)} data-testid="track-input" />
        <button className="btn btn-brand" data-testid="track-submit"><Search className="w-4 h-4" /> Track</button>
      </form>

      {q.isLoading && current && <p className="mt-6 text-ink-500">Looking up…</p>}
      {q.isError && current && <p className="mt-6 text-rose-600">We couldn’t find that tracking number, or it isn’t linked to your account.</p>}

      {q.data && (
        <div className="mt-6 card space-y-4" data-testid="tracking-result">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs uppercase text-ink-500 tracking-widest font-semibold">Shipment</p>
              <h2 className="text-2xl font-bold">{q.data.trackingNumber}</h2>
            </div>
            <StatusBadge value={q.data.status} />
          </div>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <div className="text-ink-500 text-xs">Destination</div>
              <div className="font-semibold">{q.data.destinationCity}, {q.data.destinationCountry}</div>
            </div>
            <div>
              <div className="text-ink-500 text-xs">Weight</div>
              <div className="font-semibold">{q.data.weightKg} kg</div>
            </div>
          </div>
          <div>
            <div className="font-semibold mb-3">Timeline</div>
            <Timeline events={q.data.timeline} />
          </div>
          <p className="text-xs text-ink-500">
            Only your shipment is shown here. Information about other customers, travelers, or luggage contents is never displayed.
          </p>
        </div>
      )}
    </div>
  );
}
