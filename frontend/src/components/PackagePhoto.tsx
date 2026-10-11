import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { ShipmentApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { preparePackagePhoto } from '../util/packagePhoto';

export function PackagePhoto({ id, trackingNumber, hasPhoto, senderId }: {
  id: string; trackingNumber: string; hasPhoto: boolean; senderId?: string;
}) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const viewer = useRef<HTMLDialogElement>(null);
  const [failedPhotos, setFailedPhotos] = useState<string[]>([]);
  const canEdit = user?.role === 'ADMIN' || (user?.role === 'SENDER' && user.id === senderId);
  const photo = useQuery({
    queryKey: ['shipment-photos', id], queryFn: () => ShipmentApi.photos(id),
    enabled: hasPhoto || canEdit, staleTime: 5 * 60 * 1000, retry: false,
  });
  const pictures = photo.data ?? [];
  const update = useMutation({
    mutationFn: async ({ files = [], replaceIndex, removeIndex }: {
      files?: File[]; replaceIndex?: number; removeIndex?: number;
    }) => {
      if (!photo.data) throw new Error('Load package pictures before editing');
      const next = [...photo.data];
      if (replaceIndex !== undefined && (replaceIndex < 0 || replaceIndex >= next.length)) {
        throw new Error('The picture is no longer available. Reload and try again.');
      }
      if (removeIndex !== undefined) next.splice(removeIndex, 1);
      if (replaceIndex === undefined && next.length + files.length > 5) {
        throw new Error('A package can have up to 5 pictures');
      }
      const prepared = await Promise.all(files.map(preparePackagePhoto));
      if (replaceIndex !== undefined) next[replaceIndex] = prepared[0];
      else next.push(...prepared);
      await ShipmentApi.updatePhotos(id, next);
      return next;
    },
    onSuccess: async (photos) => {
      setFailedPhotos([]);
      qc.setQueryData(['shipment-photos', id], photos);
      qc.removeQueries({ queryKey: ['shipment-photo', id] });
      await Promise.all(['shipments', 'my-shipments', 'luggage', 'trip', 'trips'].map(key =>
        qc.invalidateQueries({ queryKey: [key] })));
      toast.success('Package pictures updated');
    },
    onError: (error: unknown) => {
      const message: unknown = isAxiosError(error) ? error.response?.data?.message : undefined;
      toast.error(typeof message === 'string' ? message : error instanceof Error ? error.message : 'Failed to update pictures');
    },
  });
  return (
    <div className="space-y-2 shrink-0">
      {photo.isError ? <div role="alert" className="text-xs text-red-600">Pictures unavailable
        <button type="button" className="underline block" onClick={() => photo.refetch()}>Retry</button>
      </div> : photo.isLoading ? <span className="text-xs text-ink-500">Loading pictures...</span>
        : pictures.length === 0 ? <span className="text-xs text-ink-500">No photo</span>
          : <div className="flex flex-wrap gap-2 max-w-xs">
            {pictures.map((src, index) => <div key={index} className="space-y-1">
              {failedPhotos.includes(src) ? <span role="alert" className="text-xs text-red-600">Picture unavailable</span>
                : <img className="w-20 h-20 object-contain rounded-lg bg-ink-100"
                  src={src} alt={`Package ${trackingNumber}, picture ${index + 1}`} draggable={false}
                  onError={() => setFailedPhotos(current => [...current, src])} />}
              {canEdit && <div className="text-xs">
                <label className="block">Replace picture {index + 1}
                  <input type="file" accept="image/*" className="block max-w-[120px]" disabled={update.isPending}
                    aria-label={`Replace picture ${index + 1} for ${trackingNumber}`}
                    onChange={e => {
                      const file = e.target.files?.[0];
                      e.target.value = '';
                      if (file) update.mutate({ files: [file], replaceIndex: index });
                    }} />
                </label>
                <button type="button" className="text-red-600 underline" disabled={update.isPending}
                  onClick={() => update.mutate({ removeIndex: index })}>Remove picture {index + 1}</button>
              </div>}
            </div>)}
          </div>}
      {pictures.length > 0 && <button type="button" className="btn btn-ghost text-xs"
        disabled={photo.isError}
        data-testid={`view-photo-${trackingNumber}`}
        onClick={() => viewer.current?.showModal()}>View pictures ({pictures.length})</button>}
      <dialog ref={viewer} className="rounded-xl p-4 max-w-[95vw] backdrop:bg-black/60"
        aria-label={`Pictures of package ${trackingNumber}`}>
        <div className="flex items-center justify-between gap-4 mb-3">
          <h2 className="font-semibold">{trackingNumber}</h2>
          <button type="button" className="btn btn-ghost text-xs" onClick={() => viewer.current?.close()}>Close</button>
        </div>
        <div className="space-y-4 max-h-[80vh] overflow-auto">
          {pictures.map((src, index) => failedPhotos.includes(src)
            ? <p key={index} role="alert">Picture {index + 1} unavailable</p>
            : <img key={index} src={src} alt={`Package ${trackingNumber}, picture ${index + 1}`}
              className="max-h-[70vh] max-w-full object-contain"
              onError={() => setFailedPhotos(current => [...current, src])} />)}
        </div>
      </dialog>
      {canEdit && <label className="block text-xs text-ink-500">
        {update.isPending ? 'Updating pictures...' : `Add pictures (${pictures.length}/5)`}
        <input className="block text-xs max-w-[180px]" type="file" accept="image/*" multiple
          disabled={update.isPending || !photo.data || photo.isError || pictures.length >= 5}
          aria-label={`Add pictures for ${trackingNumber}`} data-testid={`update-photo-${trackingNumber}`}
          onChange={e => {
            const files = Array.from(e.target.files ?? []);
            e.target.value = '';
            if (files.length) update.mutate({ files });
          }} />
      </label>}
    </div>
  );
}
