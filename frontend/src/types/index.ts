export type Role = 'ADMIN' | 'AGENT' | 'TRAVELER' | 'SENDER';

export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  role: Role;
  active: boolean;
  mustChangePassword: boolean;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  expiresInMs: number;
  user: User;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  password: string;
  role: 'SENDER' | 'TRAVELER';
}

export interface PublicTravel {
  id: string;
  origin: string;
  destination: string;
  departureDate: string;
  availableCapacityKg: number;
  createdAt: string;
}

export interface Recipient {
  id: string;
  name: string;
  phone: string;
  city: string;
  address?: string;
  notes?: string;
}

export interface Shipment {
  id: string;
  trackingNumber: string;
  senderId: string | null;
  senderName: string;
  sourceRequestId: string | null;
  recipient: Recipient;
  recipients: Recipient[];
  photoCount: number;
  destinationCountry: string;
  destinationCity: string;
  description?: string;
  declaredWeightKg: string;
  actualWeightKg?: string;
  ratePerKg: string;
  shippingFee: string;
  pickupFee: string;
  totalAmount: string;
  paymentStatus: string;
  hasPhoto?: boolean;
  packageType: 'DOCUMENT' | 'MEDICINE' | 'OTHER';
  pickupMethod: 'ALEXANDRIA_DROPOFF' | 'PICKUP';
  pickupAddress?: string;
  pickupRequestedDate?: string;
  status: string;
  referringAgentId?: string;
  luggageId?: string;
  luggageNumber?: string;
  tripId?: string | null;
  tripNumber?: string | null;
  assignedAgentId?: string;
  assignedAgentName?: string;
  assignedTravelerId?: string;
  assignedTravelerName?: string;
  createdAt: string;
  updatedAt: string;
  travelAnnouncementId?: string | null;
  travelRoute?: string | null;
  travelDepartureDate?: string | null;
  travelBookingStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | null;
  queueStatus?: PackageQueueStatus | null;
  queueExpiresAt?: string | null;
}

export type PackageQueueStatus = 'QUEUED' | 'RESERVED' | 'APPROVED' | 'PAYMENT_UPLOADED';

export interface QueuePackage {
  id: string;
  trackingNumber: string;
  destinationCountry: string;
  destinationCity: string;
  description: string | null;
  packageType: string;
  weightKg: number;
  shipmentPrice: number;
  serviceFee: number | null;
  shipmentStatus: string;
  queueStatus: PackageQueueStatus | 'LEGACY';
  claimantId: string | null;
  claimantName: string | null;
  expiresAt: string | null;
  serverTime: string;
  createdAt: string;
  paymentUploadedAt: string | null;
  photos: string[];
}

export interface PackageNotification {
  id: string;
  shipmentId: string | null;
  requestId: string | null;
  message: string;
  createdAt: string;
  readAt: string | null;
}

export interface SenderContact {
  name: string;
  email: string;
  phone: string | null;
  pickupAddress: string | null;
}

export interface TrackingEvent {
  id: string;
  eventType: string;
  status?: string;
  description?: string;
  location?: string;
  createdAt: string;
}

export interface PublicTracking {
  trackingNumber: string;
  destinationCity: string;
  destinationCountry: string;
  weightKg: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  timeline: TrackingEvent[];
}

export interface ShipmentSummary {
  id: string;
  trackingNumber: string;
  declaredWeightKg: string;
  destinationCity: string;
  status: string;
  senderName: string;
  hasPhoto: boolean;
  recipientName: string;
  recipientPhone: string;
  recipients: Recipient[];
  photoCount: number;
  description?: string | null;
}

export interface Luggage {
  id: string;
  assignedAgentId?: string;
  assignedAgentName?: string;
  assignedTravelerId?: string;
  assignedTravelerName?: string;
  luggageNumber: string;
  tripId: string | null;
  tripNumber: string | null;
  luggageType?: string;
  capacityKg: string;
  assignedWeightKg: string;
  availableCapacityKg: string;
  actualWeightKg?: string;
  status: string;
  sealNumber?: string;
  notes?: string;
  shipments: ShipmentSummary[];
}

export interface Trip {
  id: string;
  tripNumber: string;
  travelerId: string;
  travelerName: string;
  departureAirport: string;
  arrivalAirport: string;
  departureDate: string;
  expectedArrivalDate?: string;
  totalLuggageCapacity: string;
  numberOfLuggage: number;
  status: string;
  notes?: string;
  assignedWeightKg: string;
  availableCapacityKg: string;
  luggage: Luggage[];
  directShipments: ShipmentSummary[];
}

export interface TravelAnnouncementRequest {
  origin: string;
  destination: string;
  departureDate: string;
  expectedArrivalDate?: string;
  notes?: string;
  totalCapacityKg: number;
  numberOfLuggage?: number;
}

export interface TravelAnnouncement extends Omit<TravelAnnouncementRequest, 'totalCapacityKg' | 'numberOfLuggage'> {
  id: string;
  postedByName: string;
  createdAt: string;
  postedById: string;
  totalCapacityKg: string | null;
  numberOfLuggage: number | null;
  approvedWeightKg: string;
  availableCapacityKg: string | null;
}

export interface TravelBookingRequest {
  shipmentId: string;
  trackingNumber: string;
  senderName: string;
  weightKg: string;
  travelAnnouncementId: string;
  route: string;
  departureDate: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
}

export interface ReferralStats {
  totalReferrals: number;
  activeShipments: number;
  deliveredShipments: number;
  totalKg: string;
  totalRevenue: string;
  commissionEarned: string;
  commissionPaid: string;
  commissionPending: string;
  referralCode: string;
}

export interface AdminStats {
  totalShipments: number;
  awaitingPickup: number;
  atAlexandria: number;
  readyForConsolidation: number;
  assignedToLuggage: number;
  activeTravelers: number;
  activeTrips: number;
  luggageInTransit: number;
  arrivedInEthiopia: number;
  pendingDeliveries: number;
  revenue: string;
  referralCommissions: string;
}

export interface PublicInfo {
  alexandriaAddress: string;
  alexandriaHours: string;
  defaultRatePerKg: string;
  defaultPickupFee: string;
  documentFee: string;
  medicineFee: string;
  brand: string;
  subtitle: string;
}
