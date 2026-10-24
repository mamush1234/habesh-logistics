import axios from 'axios';
import { api } from './client';
import type { MarketRequestInput } from './marketplace';
import type { Shipment } from '../types';

export interface GuestLink {
  id: string; label: string; createdAt: string; expiresAt: string;
  status: 'OPEN' | 'SUBMITTED' | 'EXPIRED' | 'REVOKED'; requestId: string | null;
}
export interface GuestStatus { status: GuestLink['status']; message: string }
export interface GuestSubmissionResult extends GuestStatus {
  credentials: { email: string; temporaryPassword: string } | null;
  accountMessage: string;
}
export interface GuestSubmission {
  token: string; name: string; email: string; phone: string; photoDataUrl: string; request: MarketRequestInput;
}
export interface GuestConversionInput {
  recipient: { name: string; phone: string; city: string; address?: string };
  destinationCountry: string; pickupMethod: 'ALEXANDRIA_DROPOFF' | 'PICKUP';
  pickupAddress?: string; pickupRequestedDate?: string; pickupTimeWindow?: string;
}

// Guest links are scoped credentials, never a full account session.
const guest = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? '' });
export const GuestRequestsApi = {
  convert: (id: string, input: GuestConversionInput) =>
    api.post<Shipment>(`/api/admin/guest-requests/${id}/shipment`, input).then(r => r.data),
  create: (label: string) => api.post<{ link: GuestLink; token: string }>('/api/admin/guest-request-links', { label }).then(r => r.data),
  list: () => api.get<GuestLink[]>('/api/admin/guest-request-links').then(r => r.data),
  revoke: (id: string) => api.post<GuestLink>(`/api/admin/guest-request-links/${id}/revoke`).then(r => r.data),
  check: (token: string) => guest.post<GuestStatus>('/api/guest-requests/check', { token }).then(r => r.data),
  submit: (input: GuestSubmission) => guest.post<GuestSubmissionResult>('/api/guest-requests/submit', input).then(r => r.data),
};
