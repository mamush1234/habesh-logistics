import { useQuery } from '@tanstack/react-query';
import { Package } from 'lucide-react';
import { MarketplaceApi } from '../api/marketplace';
import { PublicApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';

export function RequestThumbnail({ id, hasPhoto, publicPreview = false }: {
  id: string; hasPhoto: boolean; publicPreview?: boolean;
}) {
  const { user } = useAuth();
  const photo = useQuery({
    queryKey: publicPreview ? ['public-request-photo', id] : ['marketplace', 'product-photo', id, user?.id],
    queryFn: () => publicPreview ? PublicApi.productPhoto(id) : MarketplaceApi.productPhoto(id),
    enabled: hasPhoto, staleTime: publicPreview ? 0 : 5 * 60_000, gcTime: publicPreview ? 0 : 5 * 60_000,
  });
  return <div className="request-thumbnail">
    {!hasPhoto ? <div className="request-photo-placeholder"><Package aria-hidden="true" /><span>No product photo</span></div>
      : photo.isPending ? <p role="status">Loading photo...</p>
      : photo.isError ? <div role="alert">Photo unavailable. <button type="button" className="underline" onClick={() => photo.refetch()}>Retry</button></div>
      : <img src={photo.data} alt="Package product" loading="lazy" />}
  </div>;
}
