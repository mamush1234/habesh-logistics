import { api } from './client';
import type { DeliveryStatus } from './marketplace';

export type ReviewDirection = 'SENDER_TO_CARRIER' | 'CARRIER_TO_SENDER';
export const carrierReviewCriteria = ['COMMUNICATION', 'ON_TIME_DELIVERY', 'PACKAGE_HANDLING', 'RELIABILITY'] as const;
export const senderReviewCriteria = ['COMMUNICATION', 'ACCURATE_DESCRIPTION', 'HANDOFF_TIMELINESS', 'PACKAGING_QUALITY'] as const;
export type ReviewCriterion = typeof carrierReviewCriteria[number] | typeof senderReviewCriteria[number];
export interface DeliveryReview {
  id: string; direction: ReviewDirection; rating: number; scores: Partial<Record<ReviewCriterion, number>>;
  comment: string | null; legacy: boolean; createdAt: string; mine: boolean;
}
export interface ReviewBooking {
  packageId: string; origin: string; destination: string; status: DeliveryStatus;
  canReview: boolean; direction: ReviewDirection | null; reviews: DeliveryReview[];
}
export interface ReviewInbox { items: ReviewBooking[]; page: number; totalPages: number; totalElements: number }
export interface ReviewInput { rating: number; scores: Partial<Record<ReviewCriterion, number>>; comment?: string }
export const ReviewsApi = {
  inbox: (page: number) => api.get<ReviewInbox>('/api/marketplace/reviews', { params: { page } }).then(r => r.data),
  forPackage: (id: string) => api.get<ReviewBooking>(`/api/marketplace/requests/${id}/reviews`).then(r => r.data),
  submit: (id: string, input: ReviewInput) =>
    api.post<ReviewBooking>(`/api/marketplace/requests/${id}/reviews`, input).then(r => r.data),
};
