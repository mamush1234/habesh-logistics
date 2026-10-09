export type Role = 'ADMIN' | 'AGENT' | 'TRAVELER' | 'SENDER';

export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  expiresInMs: number;
  user: User;
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
  senderId: string;
  senderName: string;
  recipient: Recipient;
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
  assignedAgentId?: string;
  assignedAgentName?: string;
  assignedTravelerId?: string;
  assignedTravelerName?: string;
  createdAt: string;
  updatedAt: string;
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
}

export interface Luggage {
  id: string;
  assignedAgentId?: string;
  assignedAgentName?: string;
  assignedTravelerId?: string;
  assignedTravelerName?: string;
  luggageNumber: string;
  tripId: string;
  tripNumber: string;
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
