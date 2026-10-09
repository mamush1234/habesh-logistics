import { api } from './client';
import type {
  AuthResponse, Shipment, PublicTracking, TrackingEvent,
  Trip, Luggage, AdminStats, ReferralStats, PublicInfo, User
} from '../types';

export const AuthApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/api/auth/login', { email, password }).then(r => r.data),
  register: (data: { firstName: string; lastName: string; email: string; phone?: string; password: string; role?: 'SENDER' | 'TRAVELER' }) =>
    api.post<AuthResponse>('/api/auth/register', data).then(r => r.data),
  me: () => api.get<User>('/api/me').then(r => r.data),
};

export const PublicApi = {
  info: () => api.get<PublicInfo>('/api/public/info').then(r => r.data),
  track: (tn: string) => api.get<PublicTracking>(`/api/shipments/tracking/${encodeURIComponent(tn)}`).then(r => r.data),
};

export const ShipmentApi = {
  create: (payload: any) => api.post<Shipment>('/api/shipments', payload).then(r => r.data),
  createGuest: (payload: any) =>
    api.post<{ trackingNumber: string }>('/api/shipments/guest', payload).then(r => r.data),
  list: () => api.get<Shipment[]>('/api/shipments').then(r => r.data),
  get: (id: string) => api.get<Shipment>(`/api/shipments/${id}`).then(r => r.data),
  events: (id: string) => api.get<TrackingEvent[]>(`/api/shipments/${id}/tracking`).then(r => r.data),
  updateStatus: (id: string, status: string, description?: string, location?: string) =>
    api.put<Shipment>(`/api/shipments/${id}/status`, { status, description, location }).then(r => r.data),
  assignAgent: (id: string, agentId: string | null) =>
    api.put<Shipment>(`/api/shipments/${id}/agent`, { agentId }).then(r => r.data),
  assignTraveler: (id: string, travelerId: string | null) =>
    api.put<Shipment>(`/api/shipments/${id}/traveler`, { travelerId }).then(r => r.data),
  updatePayment: (id: string, paymentStatus: string) =>
    api.put<Shipment>(`/api/shipments/${id}/payment`, { paymentStatus }).then(r => r.data),
  remove: (id: string) => api.delete(`/api/shipments/${id}`).then(r => r.data),
  photo: (id: string) => api.get<{ photo: string }>(`/api/shipments/${id}/photo`).then(r => r.data.photo),
};

export const TripApi = {
  create: (payload: any) => api.post<Trip>('/api/trips', payload).then(r => r.data),
  list: () => api.get<Trip[]>('/api/trips').then(r => r.data),
  get: (id: string) => api.get<Trip>(`/api/trips/${id}`).then(r => r.data),
  updateStatus: (id: string, status: string) =>
    api.put<Trip>(`/api/trips/${id}/status`, { status }).then(r => r.data),
};

export const LuggageApi = {
  get: (id: string) => api.get<Luggage>(`/api/luggage/${id}`).then(r => r.data),
  assign: (luggageId: string, shipmentId: string) =>
    api.post<Luggage>(`/api/luggage/${luggageId}/shipments`, { shipmentId }).then(r => r.data),
  unassign: (luggageId: string, shipmentId: string) =>
    api.delete<Luggage>(`/api/luggage/${luggageId}/shipments/${shipmentId}`).then(r => r.data),
  updateStatus: (id: string, status: string) =>
    api.put<Luggage>(`/api/luggage/${id}/status`, { status }).then(r => r.data),
  autoAssign: (tripId: string) =>
    api.post<{ assigned: number }>(`/api/luggage/trips/${tripId}/auto-assign`).then(r => r.data),
  assignAgent: (id: string, agentId: string | null) =>
    api.put<Luggage>(`/api/luggage/${id}/agent`, { agentId }).then(r => r.data),
  assignTraveler: (id: string, travelerId: string | null) =>
    api.put<Luggage>(`/api/luggage/${id}/traveler`, { travelerId }).then(r => r.data),
};

export const PickupApi = {
  create: (payload: any) => api.post('/api/pickups', payload).then(r => r.data),
  list: () => api.get('/api/pickups').then(r => r.data as any[]),
  updateStatus: (id: string, status: string) =>
    api.put(`/api/pickups/${id}/status`, { status }).then(r => r.data),
};

export const AdminApi = {
  agentCodes: () => api.get<{ name: string; referralCode: string }[]>('/api/admin/agent-codes').then(r => r.data),
  dashboard: () => api.get<AdminStats>('/api/admin/dashboard').then(r => r.data),
  users: (role?: string) => api.get<User[]>('/api/admin/users', { params: role ? { role } : {} }).then(r => r.data),
  createUser: (data: { firstName: string; lastName: string; email: string; phone?: string; password: string; role: 'TRAVELER' | 'AGENT' | 'SENDER' }) =>
    api.post<User>('/api/admin/users', data).then(r => r.data),
};

export const AgentApi = {
  myStats: () => api.get<ReferralStats>('/api/agents/me/referrals').then(r => r.data),
  myReferrals: () => api.get<Shipment[]>('/api/agents/me/referred-shipments').then(r => r.data),
};

export const ScanApi = {
  scan: (code: string, action?: string) =>
    api.post<{ type: string; shipment?: Shipment; luggage?: Luggage }>('/api/scan', { code, action }).then(r => r.data),
};

export const qrUrl = {
  shipment: (id: string) => `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/qr/shipment/${id}`,
  luggage: (id: string) => `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/qr/luggage/${id}`,
};
