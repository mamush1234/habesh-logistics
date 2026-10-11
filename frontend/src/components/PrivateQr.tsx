import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { QrApi } from '../api/endpoints';

export function PrivateQr({ kind, value, inline = false }: {
  kind: 'shipment' | 'luggage' | 'tracking'; value: string; inline?: boolean;
}) {
  const viewer = useRef<HTMLDialogElement>(null);
  const [url, setUrl] = useState('');
  const [requested, setRequested] = useState(inline);
  const image = useQuery({
    queryKey: ['qr', kind, value], queryFn: () => QrApi.image(kind, value),
    enabled: requested, retry: false,
  });
  useEffect(() => {
    if (!image.data) return;
    const next = URL.createObjectURL(image.data);
    setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [image.data]);
  const content = image.isError ? <div role="alert">QR unavailable.
    <button type="button" className="btn btn-ghost" onClick={() => image.refetch()}>Retry</button>
  </div> : url ? <img src={url} alt={`QR code for ${value}`} className="w-56 h-56 mx-auto" />
    : <p role="status">Loading QR...</p>;
  if (inline) return content;
  return <>
    <button type="button" className="btn btn-ghost text-xs" onClick={() => {
      setRequested(true);
      viewer.current?.showModal();
    }}>View QR</button>
    <dialog ref={viewer} className="rounded-xl p-4 backdrop:bg-black/60" aria-label={`QR for ${value}`}>
      <button type="button" className="btn btn-ghost mb-3" onClick={() => viewer.current?.close()}>Close</button>
      {content}
    </dialog>
  </>;
}
