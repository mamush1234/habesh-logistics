import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { TravelAnnouncementApi } from '../api/endpoints';

export function TravelRequests() {
  const qc = useQueryClient();
  const requests = useQuery({
    queryKey: ['travel-requests'],
    queryFn: TravelAnnouncementApi.requests,
    refetchInterval: 30_000,
  });
  const travel = useQuery({ queryKey: ['travel-announcements'], queryFn: TravelAnnouncementApi.list });
  const decision = useMutation({
    mutationFn: ({ id, shipmentId, status }: { id: string; shipmentId: string; status: 'APPROVED' | 'REJECTED' }) =>
      TravelAnnouncementApi.decide(id, shipmentId, status),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['travel-requests'] });
      qc.invalidateQueries({ queryKey: ['travel-announcements'] });
      qc.invalidateQueries({ queryKey: ['shipments'] });
      qc.invalidateQueries({ queryKey: ['my-shipments'] });
      toast.success('Travel request updated');
    },
    onError: (error: unknown) => {
      qc.invalidateQueries({ queryKey: ['travel-announcements'] });
      toast.error(isAxiosError<{ message?: string }>(error)
        ? error.response?.data?.message || 'Could not update request' : 'Could not update request');
    },
  });
  return <section className="card space-y-3" aria-label="Travel space requests">
    <h2 className="text-xl font-semibold">Travel space requests</h2>
    <p className="text-sm text-ink-500">Only requests for your travel posts are shown (admins see all). Pending requests do not hold space.</p>
    {requests.isPending && <p role="status">Loading requests...</p>}
    {(requests.isError || travel.isError) && <p role="alert">
      Could not load travel requests or capacity. <button type="button" className="underline"
        onClick={() => { requests.refetch(); travel.refetch(); }}>Retry</button>
    </p>}
    {requests.data?.length === 0 && <p className="text-sm text-ink-500">No travel requests yet.</p>}
    {requests.data?.map(r => {
      const announcement = travel.data?.find(a => a.id === r.travelAnnouncementId);
      const fits = announcement?.availableCapacityKg != null && Number(r.weightKg) <= Number(announcement.availableCapacityKg);
      return <article key={r.shipmentId} className="rounded-xl ring-1 ring-ink-300 p-4 space-y-2">
        <h3 className="font-semibold">{r.route} &middot; {new Date(`${r.departureDate}T00:00:00`).toLocaleDateString()}</h3>
        <p className="text-sm">{r.trackingNumber} &middot; {r.senderName} &middot; <strong>{r.weightKg} kg</strong></p>
        <p className="text-sm">Request: <strong>{r.status}</strong>
          {announcement?.availableCapacityKg != null && <> &middot; {announcement.availableCapacityKg} kg space remaining</>}
        </p>
        {r.status === 'PENDING' && <div className="space-y-2">
          {announcement && !fits && <p className="text-sm text-red-700">Not enough space to approve this request.</p>}
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn btn-brand" disabled={decision.isPending || !fits}
              onClick={() => decision.mutate({ id: r.travelAnnouncementId, shipmentId: r.shipmentId, status: 'APPROVED' })}>Approve {r.weightKg} kg</button>
            <button type="button" className="btn btn-ghost" disabled={decision.isPending}
              onClick={() => decision.mutate({ id: r.travelAnnouncementId, shipmentId: r.shipmentId, status: 'REJECTED' })}>Reject</button>
          </div>
        </div>}
      </article>;
    })}
  </section>;
}
