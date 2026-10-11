import { api } from './client';
import type {
  AuthResponse, Shipment, PublicTracking, TrackingEvent,
  Trip, Luggage, AdminStats, ReferralStats, PublicInfo, User, Invitation, InvitationInfo, RegisterRequest
} from '../types';

export const AuthApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/api/auth/login', { email, password }).then(r => r.data),
  register: (data: RegisterRequest) =>
    api.post<AuthResponse>('/api/auth/register', data).then(r => r.data),
  invitation: (token: string) => api.post<InvitationInfo>('/api/auth/invitation', { token }).then(r => r.data),
  me: () => api.get<User>('/api/me').then(r => r.data),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.put('/api/me/password', { currentPassword, newPassword }).then(r => r.data),
};

export const PublicApi = {
  info: () => api.get<PublicInfo>('/api/public/info').then(r => r.data),
  track: (tn: string) => api.get<PublicTracking>(`/api/shipments/tracking/${encodeURIComponent(tn)}`).then(r => r.data),
};

export const ShipmentApi = {
  create: (payload: any) => api.post<Shipment>('/api/shipments', payload).then(r => r.data),
  list: () => api.get<Shipment[]>('/api/shipments').then(r => r.data),
  get: (id: string) => api.get<Shipment>(`/api/shipments/${id}`).then(r => r.data),
  events: (id: string) => api.get<TrackingEvent[]>(`/api/shipments/${id}/tracking`).then(r => r.data),
  updateStatus: (id: string, status: string, description?: string, location?: string) =>
    api.put<Shipment>(`/api/shipments/${id}/status`, { status, description, location }).then(r => r.data),
  assignAgent: (id: string, agentId: string | null) =>
    api.put<Shipment>(`/api/shipments/${id}/agent`, { agentId }).then(r => r.data),
  assignTraveler: (id: string, travelerId: string | null) =>
    api.put<Shipment>(`/api/shipments/${id}/traveler`, { travelerId }).then(r => r.data),
  assignTrip: (id: string, tripId: string | null) =>
    api.put<Shipment>(`/api/shipments/${id}/trip`, { tripId }).then(r => r.data),
  updatePayment: (id: string, paymentStatus: string) =>
    api.put<Shipment>(`/api/shipments/${id}/payment`, { paymentStatus }).then(r => r.data),
  remove: (id: string) => api.delete(`/api/shipments/${id}`).then(r => r.data),
  photo: (id: string) => api.get<{ photo: string }>(`/api/shipments/${id}/photo`).then(r => r.data.photo),
  updatePhoto: (id: string, photoDataUrl: string) =>
    api.put<Shipment>(`/api/shipments/${id}/photo`, { photoDataUrl }).then(r => r.data),
  photos: (id: string) => api.get<{ photos: string[] }>(`/api/shipments/${id}/photos`).then(r => r.data.photos),
  updatePhotos: (id: string, photoDataUrls: string[]) =>
    api.put<Shipment>(`/api/shipments/${id}/photos`, { photoDataUrls }).then(r => r.data),
};

export const TripApi = {
  create: (payload: any) => api.post<Trip>('/api/trips', payload).then(r => r.data),
  list: () => api.get<Trip[]>('/api/trips').then(r => r.data),
  get: (id: string) => api.get<Trip>(`/api/trips/${id}`).then(r => r.data),
  updateStatus: (id: string, status: string) =>
    api.put<Trip>(`/api/trips/${id}/status`, { status }).then(r => r.data),
};

export const LuggageApi = {
  list: () => api.get<Luggage[]>('/api/luggage').then(r => r.data),
  create: (payload: { tripId?: string; luggageType?: string; capacityKg: number; sealNumber?: string; notes?: string }) =>
    api.post<Luggage>('/api/luggage', payload).then(r => r.data),
  assignTrip: (id: string, tripId: string | null) =>
    api.put<Luggage>(`/api/luggage/${id}/trip`, { tripId }).then(r => r.data),
  get: (id: string) => api.get<Luggage>(`/api/luggage/${id}`).then(r => r.data),
  assign: (luggageId: string, shipmentId: string) =>
    api.post<Luggage>(`/api/luggage/${luggageId}/shipments`, { shipmentId }).then(r => r.data),
  assignBatch: (luggageId: string, shipmentIds: string[]) =>
    api.post<Luggage>(`/api/luggage/${luggageId}/shipments/batch`, { shipmentIds }).then(r => r.data),
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
  invitations: () => api.get<Invitation[]>('/api/admin/invitations').then(r => r.data),
  invite: (email: string, role: 'SENDER' | 'TRAVELER' | 'AGENT') =>
    api.post<{ invitation: Invitation; token: string; invitationUrl: string; emailSent: boolean; deliveryMessage: string }>(
      '/api/admin/invitations', { email, role }).then(r => r.data),
  revokeInvitation: (id: string) => api.delete(`/api/admin/invitations/${id}`).then(r => r.data),
  updateAccess: (id: string, active: boolean) =>
    api.put<User>(`/api/admin/users/${id}/access`, { active }).then(r => r.data),
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

export const QrApi = {
  image: (kind: 'shipment' | 'luggage' | 'tracking', value: string) =>
    api.get<Blob>(`/api/qr/${kind}/${encodeURIComponent(value)}`, { responseType: 'blob' }).then(r => r.data),
};
