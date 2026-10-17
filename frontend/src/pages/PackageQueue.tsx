import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PackageQueueApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { StatusBadge } from '../components/StatusBadge';

function PaymentProof({ id }: { id: string }) {
  const [show, setShow] = useState(false);
  const query = useQuery({ queryKey: ['legacy-proof', id], queryFn: () => PackageQueueApi.proof(id), enabled: show });
  return <div><button className="underline text-sm" onClick={() => setShow(!show)}>{show ? 'Hide' : 'View'} historical payment confirmation</button>
    {show && query.isPending && <p role="status">Loading...</p>}
    {show && query.isError && <p role="alert">Could not load confirmation. <button className="underline" onClick={() => query.refetch()}>Retry</button></p>}
    {show && query.data && <img src={query.data} alt="Historical payment confirmation" className="max-h-96 object-contain" />}
  </div>;
}
export function PackageQueue() {
  const { user } = useAuth();
  const [status, setStatus] = useState('');
  const query = useQuery({ queryKey: ['legacy-packages', user?.id], queryFn: PackageQueueApi.list });
  return <div className="space-y-5"><h1 className="text-3xl font-bold">Legacy package archive</h1>
    <p>The old queue is read-only. Reservations, decisions and payment-proof uploads have been replaced by marketplace offers and demo escrow.</p>
    <Link to="/marketplace" className="btn btn-primary">Open marketplace</Link>
    <label className="label">Filter shipment status<input className="input" value={status} onChange={e => setStatus(e.target.value)} /></label>
    {query.isPending && <p role="status">Loading archive...</p>}
    {query.isError && <p role="alert">Could not load archive. <button className="underline" onClick={() => query.refetch()}>Retry</button></p>}
    {query.data?.filter(s => s.shipmentStatus.includes(status.toUpperCase())).map(s => <article key={s.id} className="card space-y-3">
      <h2 className="text-xl font-bold">{s.trackingNumber}</h2><StatusBadge value={s.shipmentStatus} />
      <p>{s.destinationCity} &middot; {s.weightKg} kg &middot; Historical queue: {s.queueStatus}</p>
      <p>{s.description}</p>{s.claimantName && <p>Original claimant: {s.claimantName}</p>}
      {s.paymentUploadedAt && <PaymentProof id={s.id} />}
      <Link className="underline" to={`/track/${s.trackingNumber}`}>Shipment tracking</Link>
    </article>)}
  </div>;
}
