import type { DeliveryStatus, ItemType, MarketRequestInput, MarketTripInput, PackageItemInput, PackageReceiver } from '../api/marketplace';

export type CustomerType = 'SENDER' | 'TRAVELER';
export type AuthorizationMethod = 'IN_PERSON' | 'PHONE' | 'WRITTEN' | 'DIGITAL';
export type AuthorizationState = 'ACTIVE' | 'REVOKED' | 'EXPIRED';
export type CommissionStatus = 'PENDING' | 'PAID' | 'CANCELLED';

export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

export interface AgentAuthorization {
  id: string; customerId: string; customerName: string; status: AuthorizationState; method: AuthorizationMethod;
  consentNote: string | null; grantedAt: string; expiresAt: string; revokedAt: string | null;
}
export interface AgentCustomer {
  id: string; name: string; email: string; phone: string | null; type: CustomerType; active: boolean;
  mustChangePassword: boolean; shipmentCount: number; tripCount: number;
  authorization: AgentAuthorization | null; createdAt: string;
}
export interface AgentActivity {
  id: string; action: string; customerName: string | null; targetType: string | null; targetId: string | null;
  details: string | null; createdAt: string;
}
export interface AgentDashboard {
  agentName: string; email: string; referralCode: string | null; commissionRate: number;
  activeCustomers: number; activeShipments: number; pendingRequests: number; totalCommissions: number;
  recentActivity: AgentActivity[]; recentCustomers: AgentCustomer[];
}
export interface AuthorizationGrant {
  confirmed: boolean; method: AuthorizationMethod; consentNote?: string; validDays?: number;
}
export interface CreateCustomerInput {
  customerType: CustomerType; firstName: string; lastName: string; email: string; phone: string;
  authorization: AuthorizationGrant;
}
export interface CustomerCreated {
  customer: AgentCustomer; credentials: { email: string; temporaryPassword: string }; authorization: AgentAuthorization;
}
export interface AgentPackage {
  id: string; customerId: string; customerName: string; pickupCity: string; destinationCity: string;
  availableFrom: string; deadline: string; itemType: ItemType; description: string; weightKg: number;
  offeredPrice: number; status: DeliveryStatus; adminApproved: boolean; hasPhoto: boolean;
  createdAt: string; updatedAt: string;
  items?: PackageItemInput[]; receiver?: PackageReceiver | null;
}
export interface PackageInput { details: MarketRequestInput; photoDataUrl?: string; draft: boolean }
export interface AgentTrip extends MarketTripInput {
  id: string; customerId: string; customerName: string; availableKg: number; createdAt: string;
}
export interface AgentCommission {
  id: string; requestId: string; route: string; customerName: string | null; rate: number; baseAmount: number;
  amount: number; status: CommissionStatus; createdAt: string; paidAt: string | null;
}
export interface CommissionPage extends Page<AgentCommission> { total: number; pending: number; paid: number }
export interface CommissionQuery { from?: string; to?: string; status?: CommissionStatus; page?: number }
export interface CustomerQuery { type?: CustomerType; q?: string; page?: number }
