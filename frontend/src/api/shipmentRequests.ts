import { api } from './client';
import type { ItemType, TripItemPrice } from './marketplace';

export interface ShipmentQuote {
  packageId: string; tripId: string; origin: string; destination: string; departureDate: string;
  items: { category: ItemType; weightKg: number; rate: number; unit: TripItemPrice['unit']; amount: number }[];
  packageAmount: number; platformFeePercent: number; feePayer: 'SENDER' | 'TRAVELER' | 'SPLIT';
  platformFee: number; senderFee: number; travelerFee: number; total: number; travelerPayout: number; quoteKey: string;
}
export interface ShipmentRequest {
  id: string; packageId: string; tripId: string; status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED';
  quote: ShipmentQuote; message: string | null; createdAt: string; expiresAt: string; adminManaged: boolean;
  proposedQuote: ShipmentQuote | null; decidedAt: string | null; canReview: boolean; canRespond: boolean;
}
export interface ShipmentDecision {
  action: 'ACCEPT' | 'DECLINE' | 'PROPOSE_PRICE' | 'CONFIRM_PRICE' | 'REJECT_PRICE';
  proposedPrice?: number; quoteKey: string;
}
export const ShipmentRequestsApi = {
  inbox: () => api.get<ShipmentRequest[]>('/api/marketplace/shipment-requests/inbox').then(r => r.data),
  decide: (id: string, input: ShipmentDecision) =>
    api.post<ShipmentRequest>(`/api/marketplace/shipment-requests/${id}/decision`, input).then(r => r.data),
  quote: (packageId: string, tripId: string) => api.get<ShipmentQuote>('/api/marketplace/shipment-requests/quote',
    { params: { packageId, tripId } }).then(r => r.data),
  submit: (input: { packageId: string; tripId: string; quoteKey: string; message?: string }) =>
    api.post<ShipmentRequest>('/api/marketplace/shipment-requests', input).then(r => r.data),
  forPackage: (packageId: string) => api.get<ShipmentRequest[]>('/api/marketplace/shipment-requests',
    { params: { packageId } }).then(r => r.data),
};
