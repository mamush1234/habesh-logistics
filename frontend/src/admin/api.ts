import { api } from '../api/client';
import type { DeliveryStatus } from '../api/marketplace';
import type { DateRange } from './dateRange';
import type {
  AdminRequestRow, AdminTripRow, AdminUserRow, CommissionRow, ListPage, ListQuery, Overview, Payments,
  PlatformSettings, Report, RequestView, SettingsInput,
} from './types';

const base = '/api/admin/console';

/** Drops empty values so the backend sees absent query params instead of empty strings. */
const clean = (params: Record<string, unknown>) =>
  Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''));

export const AdminConsoleApi = {
  overview: (range: DateRange) => api.get<Overview>(`${base}/overview`, { params: range }).then(r => r.data),
  users: (query: ListQuery & { type?: string }) =>
    api.get<ListPage<AdminUserRow>>(`${base}/users`, { params: clean({ ...query }) }).then(r => r.data),
  trips: (query: ListQuery) => api.get<ListPage<AdminTripRow>>(`${base}/trips`, { params: clean({ ...query }) }).then(r => r.data),
  requests: (view: RequestView, query: ListQuery) =>
    api.get<ListPage<AdminRequestRow>>(`${base}/requests`, { params: clean({ view, ...query }) }).then(r => r.data),
  resolveDispute: (id: string, outcome: Extract<DeliveryStatus, 'COMPLETED' | 'CANCELLED'>, reason: string) =>
    api.post<AdminRequestRow>(`${base}/disputes/${id}/resolve`, { outcome, reason }).then(r => r.data),
  payments: (range: DateRange, query: ListQuery) =>
    api.get<Payments>(`${base}/payments`, { params: clean({ ...range, ...query }) }).then(r => r.data),
  commissions: (query: ListQuery) =>
    api.get<ListPage<CommissionRow>>(`${base}/commissions`, { params: clean({ ...query }) }).then(r => r.data),
  payCommission: (id: string) => api.post(`/api/admin/agents/commissions/${id}/pay`).then(r => r.data),
  report: (range: DateRange) => api.get<Report>(`${base}/reports`, { params: range }).then(r => r.data),
  settings: () => api.get<PlatformSettings>(`${base}/settings`).then(r => r.data),
  updateSettings: (input: SettingsInput) => api.put<PlatformSettings>(`${base}/settings`, input).then(r => r.data),
};
