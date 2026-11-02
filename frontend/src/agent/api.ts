import { api } from '../api/client';
import type {
  AgentAuthorization, AgentCustomer, AgentDashboard, AgentPackage, AgentTrip, AuthorizationMethod, CommissionPage,
  CommissionQuery, CreateCustomerInput, CustomerCreated, CustomerQuery, Page, PackageInput,
} from './types';
import type { DeliveryStatus, MarketTripInput } from '../api/marketplace';
import { agentMocks } from './mocks';

/**
 * Single switch for temporary mock data. All /api/agent endpoints are live, so this stays off unless
 * VITE_AGENT_MOCKS=true is set for UI-only work without a backend.
 */
export const USE_AGENT_MOCKS = import.meta.env.VITE_AGENT_MOCKS === 'true';

const live = {
  dashboard: async () => (await api.get<AgentDashboard>('/api/agent/dashboard')).data,
  customers: async (query: CustomerQuery) =>
    (await api.get<Page<AgentCustomer>>('/api/agent/customers', { params: clean(query) })).data,
  customer: async (id: string) => (await api.get<AgentCustomer>(`/api/agent/customers/${id}`)).data,
  createCustomer: async (input: CreateCustomerInput) =>
    (await api.post<CustomerCreated>('/api/agent/customers', input)).data,
  createPackage: async (customerId: string, input: PackageInput) =>
    (await api.post<AgentPackage>(`/api/agent/customers/${customerId}/packages`, input)).data,
  submitDraft: async (customerId: string, packageId: string) =>
    (await api.post<AgentPackage>(`/api/agent/customers/${customerId}/packages/${packageId}/submit`)).data,
  createTrip: async (customerId: string, input: MarketTripInput) =>
    (await api.post<AgentTrip>(`/api/agent/customers/${customerId}/trips`, input)).data,
  packages: async (status?: DeliveryStatus, page = 0) =>
    (await api.get<Page<AgentPackage>>('/api/agent/packages', { params: clean({ status, page }) })).data,
  trips: async (page = 0) => (await api.get<Page<AgentTrip>>('/api/agent/trips', { params: { page } })).data,
  commissions: async (query: CommissionQuery) =>
    (await api.get<CommissionPage>('/api/agent/commissions', { params: clean(query) })).data,
  authorizations: async () => (await api.get<AgentAuthorization[]>('/api/agent/authorizations')).data,
  grantAuthorization: async (input: { customerId: string; method: AuthorizationMethod; consentNote?: string; validDays?: number }) =>
    (await api.post<AgentAuthorization>('/api/agent/authorizations', input)).data,
  revokeAuthorization: async (id: string) => (await api.delete<AgentAuthorization>(`/api/agent/authorizations/${id}`)).data,
};

function clean<T extends object>(params: T) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== '' && v !== null));
}

export type AgentApiShape = typeof live;

export const AgentApi: AgentApiShape = USE_AGENT_MOCKS ? agentMocks : live;
