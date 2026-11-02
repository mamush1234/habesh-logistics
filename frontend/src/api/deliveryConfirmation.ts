import { api } from './client';

export interface DeliveryConfirmationStatus {
  required: boolean;
  canIssue: boolean;
  canVerify: boolean;
  expiresAt: string | null;
  confirmedAt: string | null;
  attemptsRemaining: number;
  retryIssueAt: string | null;
}
export interface IssuedDeliveryCode { code: string; expiresAt: string }
const path = (id: string) => `/api/marketplace/requests/${id}/delivery-confirmation`;
export const DeliveryConfirmationApi = {
  status: (id: string) => api.get<DeliveryConfirmationStatus>(path(id)).then(r => r.data),
  issue: (id: string) => api.post<IssuedDeliveryCode>(`${path(id)}/code`).then(r => r.data),
  verify: (id: string, code: string) => api.post<DeliveryConfirmationStatus>(`${path(id)}/verify`, { code }).then(r => r.data),
};
