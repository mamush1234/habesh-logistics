import { api } from './client';

export const itemTypes = ['DOCUMENTS', 'CLOTHING', 'ELECTRONICS', 'MEDICINE', 'FOOD', 'OTHER'] as const;
export type ItemType = typeof itemTypes[number];
export const deliveryStatuses = ['REQUESTED', 'MATCHED', 'PAID_ESCROW', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'DISPUTED'] as const;
export type DeliveryStatus = typeof deliveryStatuses[number];
export type VerificationLevel = 'UNVERIFIED' | 'ID_VERIFIED' | 'FULLY_VERIFIED';
export interface MarketTraveler {
  id: string; name: string; rating: number | null; ratingCount: number; verification: VerificationLevel;
}
export interface MarketTripInput {
  origin: string; destination: string; departureDate: string; expectedArrivalDate?: string;
  capacityKg: number; allowedItems: ItemType[]; blockedItems: ItemType[];
}
export interface MarketTrip extends MarketTripInput {
  id: string; traveler: MarketTraveler; availableKg: number;
}
export interface MarketRequestInput {
  pickupCity: string; destinationCity: string; availableFrom: string; deadline: string;
  itemType: ItemType; description: string; weightKg: number; offeredPrice: number;
}
export interface MarketOffer {
  id: string; tripId: string; traveler: MarketTraveler; price: number; message: string | null;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN'; score: number;
}
export interface MarketContact { name: string; email: string; phone: string | null }
export interface MarketRequest extends MarketRequestInput {
  id: string; senderId: string; status: DeliveryStatus;
  escrowStatus: 'NONE' | 'DEMO_HELD' | 'DEMO_RELEASED' | 'DEMO_REFUNDED';
  escrowAmount: number | null; acceptedOfferId: string | null; rating: number | null;
  disputeReason: string | null; offers: MarketOffer[]; createdAt: string;
  timeline: { status: DeliveryStatus; description: string; createdAt: string }[];
  senderContact: MarketContact | null; travelerContact: MarketContact | null;
}
export interface MarketMatch { request: MarketRequest; trip: MarketTrip; price: number; score: number }

export const MarketplaceApi = {
  trips: () => api.get<MarketTrip[]>('/api/marketplace/trips').then(r => r.data),
  createTrip: (input: MarketTripInput) => api.post<MarketTrip>('/api/marketplace/trips', input).then(r => r.data),
  requests: () => api.get<MarketRequest[]>('/api/marketplace/requests').then(r => r.data),
  request: (id: string) => api.get<MarketRequest>(`/api/marketplace/requests/${id}`).then(r => r.data),
  createRequest: (input: MarketRequestInput) => api.post<MarketRequest>('/api/marketplace/requests', input).then(r => r.data),
  requestMatches: (id: string) => api.get<MarketMatch[]>(`/api/marketplace/requests/${id}/matches`).then(r => r.data),
  tripMatches: (id: string) => api.get<MarketMatch[]>(`/api/marketplace/trips/${id}/matches`).then(r => r.data),
  offer: (id: string, tripId: string, price: number, message?: string) =>
    api.post<MarketRequest>(`/api/marketplace/requests/${id}/offers`, { tripId, price, message }).then(r => r.data),
  decideOffer: (id: string, offerId: string, action: 'accept' | 'decline' | 'withdraw') =>
    api.post<MarketRequest>(`/api/marketplace/requests/${id}/offers/${offerId}/${action}`).then(r => r.data),
  fund: (id: string) => api.post<MarketRequest>(`/api/marketplace/requests/${id}/demo-escrow`).then(r => r.data),
  transition: (id: string, status: DeliveryStatus, reason?: string) =>
    api.put<MarketRequest>(`/api/marketplace/requests/${id}/status`, { status, reason }).then(r => r.data),
  rate: (id: string, rating: number) => api.post<MarketRequest>(`/api/marketplace/requests/${id}/rating`, { rating }).then(r => r.data),
  verify: (id: string, level: VerificationLevel) =>
    api.put<MarketTraveler>(`/api/marketplace/travelers/${id}/verification`, { level }).then(r => r.data),
};
