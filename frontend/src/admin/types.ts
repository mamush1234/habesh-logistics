import type { DeliveryStatus } from '../api/marketplace';
import type { Role } from '../types';

export type EscrowStatus = import('../api/marketplace').MarketRequest['escrowStatus'];
export type FeePayer = 'SENDER' | 'TRAVELER' | 'SPLIT';
export type UserStatus = 'VERIFIED' | 'PENDING' | 'SUSPENDED';
export type RequestView = 'all' | 'bookings' | 'tracking' | 'disputes' | 'reviews';

export interface ListPage<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  counts: Record<string, number>;
}

export interface ChartPoint { date: string; revenue: number; bookings: number }
export interface Activity { type: 'DELIVERY' | 'AGENT' | 'USER'; title: string; detail: string; at: string }
export interface Overview {
  from: string; to: string; bucket: 'DAY' | 'WEEK'; platformFeePercent: number;
  totalRevenue: number; platformFees: number; activeShipments: number; pendingReviews: number;
  totalBookings: number; chart: ChartPoint[]; activities: Activity[];
}

export interface AdminUserRow {
  id: string; name: string; email: string; phone: string | null; role: Role; status: UserStatus;
  active: boolean; createdAt: string | null; shipments: number; trips: number;
}

export interface AdminTripRow {
  id: string; travelerId: string; travelerName: string; travelerEmail: string; origin: string; destination: string;
  departureDate: string; expectedArrivalDate: string | null; capacityKg: number; reservedKg: number; offers: number;
  status: 'UPCOMING' | 'IN_PROGRESS' | 'COMPLETED'; agentCreated: boolean; createdAt: string;
}

export interface AdminRequestRow {
  id: string; senderName: string | null; senderEmail: string | null; pickupCity: string; destinationCity: string;
  itemType: string; description: string; weightKg: number; offeredPrice: number; status: DeliveryStatus;
  adminApproved: boolean; escrowStatus: EscrowStatus; escrowAmount: number | null; travelerName: string | null;
  acceptedPrice: number | null; offers: number; rating: number | null; disputeReason: string | null; guest: boolean;
  agentCreated: boolean; deadline: string; lastEvent: string | null; lastEventAt: string | null;
  createdAt: string; updatedAt: string;
}

export interface PaymentRow {
  requestId: string; senderName: string | null; travelerName: string | null; route: string; amount: number;
  platformFee: number; travelerPayout: number; escrowStatus: EscrowStatus; status: DeliveryStatus; fundedAt: string;
}
export interface Payments {
  summary: { held: number; released: number; refunded: number; platformFees: number };
  page: ListPage<PaymentRow>;
}

export interface CommissionRow {
  id: string; agentName: string; customerName: string | null; requestId: string; route: string; rate: number;
  baseAmount: number; amount: number; status: 'PENDING' | 'PAID' | 'CANCELLED'; createdAt: string; paidAt: string | null;
}

export interface Report {
  from: string; to: string; requestsByStatus: Record<string, number>; usersByRole: Record<string, number>;
  topRoutes: { route: string; requests: number; value: number }[]; averageRating: number | null; ratings: number;
}

export interface PlatformSettings {
  platformFeePercent: number; agentCommissionPercent: number; feePayer: FeePayer;
  updatedAt: string; updatedBy: string | null;
}
export type SettingsInput = Pick<PlatformSettings, 'platformFeePercent' | 'agentCommissionPercent' | 'feePayer'>;

export interface ListQuery { q?: string; status?: string; page?: number }
