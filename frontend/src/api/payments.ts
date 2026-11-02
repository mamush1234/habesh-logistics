import { api } from './client';
import type { ShipmentQuote } from './shipmentRequests';

export type StripePaymentStatus = 'PENDING' | 'SUCCEEDED' | 'CANCELLED' | 'REFUNDED';
export interface StripePayment {
  packageId: string; status: StripePaymentStatus | null; quote: ShipmentQuote;
  configured: boolean; canPay: boolean; publishableKey: string | null; testMode: boolean;
}
export interface StripeIntent { clientSecret: string; publishableKey: string; testMode: boolean }
export interface StripeAdminPage {
  items: { id: string; packageId: string; status: StripePaymentStatus; amountCents: number;
    currency: string; createdAt: string; updatedAt: string; testMode: boolean }[];
  page: number; totalPages: number; totalElements: number;
}
export const PaymentsApi = {
  status: (id: string) => api.get<StripePayment>(`/api/marketplace/requests/${id}/payment`).then(r => r.data),
  start: (id: string) => api.post<StripeIntent>(`/api/marketplace/requests/${id}/payment/intent`).then(r => r.data),
  admin: (page: number) => api.get<StripeAdminPage>('/api/admin/stripe-test-payments', { params: { page } }).then(r => r.data),
};
