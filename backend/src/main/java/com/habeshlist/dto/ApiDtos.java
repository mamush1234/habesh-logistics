package com.habeshlist.dto;

import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response DTOs grouped as nested records. */
public final class ApiDtos {
    private ApiDtos() {}

    public record UserDto(UUID id, String firstName, String lastName, String email,
                          String phone, Role role, boolean active, Instant createdAt, boolean mustChangePassword) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                    u.getPhone(), u.getRole(), u.isActive(), u.getCreatedAt(), u.isMustChangePassword());
        }
    }

    public record AuthResponse(String token, long expiresInMs, UserDto user) {}

    public record PublicTravelDto(UUID id, String origin, String destination, LocalDate departureDate,
                                   BigDecimal availableCapacityKg, Instant createdAt) {}

    public record PublicRequestDto(UUID id, String pickupCity, String destinationCity, LocalDate availableFrom,
            LocalDate deadline, com.habeshlist.entity.enums.MarketplaceTypes.ItemType itemType,
            BigDecimal weightKg, BigDecimal offeredPrice, boolean hasProductPhoto, Instant createdAt) {}

    public record RecipientDto(UUID id, String name, String phone, String city, String address, String notes) {
        public static RecipientDto from(Recipient r) {
            return new RecipientDto(r.getId(), r.getName(), r.getPhone(), r.getCity(), r.getAddress(), r.getNotes());
        }
    }

    public record ShipmentDto(
            UUID id, String trackingNumber,
            UUID senderId, String senderName,
            RecipientDto recipient,
            String destinationCountry, String destinationCity, String description,
            BigDecimal declaredWeightKg, BigDecimal actualWeightKg, BigDecimal ratePerKg,
            BigDecimal shippingFee, BigDecimal pickupFee, BigDecimal totalAmount,
            String paymentStatus, String packageType, String pickupMethod, String pickupAddress, LocalDate pickupRequestedDate,
            String status, UUID referringAgentId, UUID luggageId, String luggageNumber,
            UUID assignedAgentId, String assignedAgentName,
            UUID assignedTravelerId, String assignedTravelerName, boolean hasPhoto,
            UUID tripId, String tripNumber,
            Instant createdAt, Instant updatedAt, List<RecipientDto> recipients, int photoCount,
            UUID travelAnnouncementId, String travelRoute, LocalDate travelDepartureDate, String travelBookingStatus,
            String queueStatus, Instant queueExpiresAt, UUID sourceRequestId) {
        public static ShipmentDto from(Shipment s) {
            return new ShipmentDto(
                s.getId(), s.getTrackingNumber(),
                s.getSender() == null ? null : s.getSender().getId(), s.senderDisplayName(),
                RecipientDto.from(s.getRecipient()),
                s.getDestinationCountry(), s.getDestinationCity(), s.getDescription(),
                s.getDeclaredWeightKg(), s.getActualWeightKg(), s.getRatePerKg(),
                s.getShippingFee(), s.getPickupFee(), s.getTotalAmount(),
                s.getPaymentStatus() == null ? null : s.getPaymentStatus().name(),
                s.getPackageType() == null ? null : s.getPackageType().name(),
                s.getPickupMethod() == null ? null : s.getPickupMethod().name(),
                s.getPickupAddress(), s.getPickupRequestedDate(),
                s.getStatus() == null ? null : s.getStatus().name(),
                s.getReferringAgent() == null ? null : s.getReferringAgent().getId(),
                s.getLuggage() == null ? null : s.getLuggage().getId(),
                s.getLuggage() == null ? null : s.getLuggage().getLuggageNumber(),
                s.getAssignedAgent() == null ? null : s.getAssignedAgent().getId(),
                s.getAssignedAgent() == null ? null : s.getAssignedAgent().getFirstName() + " " + s.getAssignedAgent().getLastName(),
                s.getAssignedTraveler() == null ? null : s.getAssignedTraveler().getId(),
                s.getAssignedTraveler() == null ? null : s.getAssignedTraveler().getFirstName() + " " + s.getAssignedTraveler().getLastName(),
                s.getPhotoData() != null && !s.getPhotoData().isBlank(),
                s.transportTrip() == null ? null : s.transportTrip().getId(),
                s.transportTrip() == null ? null : s.transportTrip().getTripNumber(),
                s.getCreatedAt(), s.getUpdatedAt(),
                s.allRecipients().stream().map(RecipientDto::from).toList(), s.allPhotos().size(),
                s.getTravelAnnouncement() == null ? null : s.getTravelAnnouncement().getId(),
                s.getTravelAnnouncement() == null ? null : s.getTravelAnnouncement().getOrigin() + " → " + s.getTravelAnnouncement().getDestination(),
                s.getTravelAnnouncement() == null ? null : s.getTravelAnnouncement().getDepartureDate(),
                s.getTravelBookingStatus() == null ? null : s.getTravelBookingStatus().name(),
                s.getQueueStatus() == null ? null : s.getQueueStatus().name(), s.getQueueExpiresAt(),
                s.getSourceRequest() == null ? null : s.getSourceRequest().getId());
        }
    }

    /** Minimal public tracking payload — never includes other customers' info. */
    public record PublicTrackingDto(
            String trackingNumber, String destinationCity, String destinationCountry,
            BigDecimal weightKg, String status, Instant createdAt, Instant updatedAt,
            List<TrackingEventDto> timeline) {}

    public record TrackingEventDto(
            UUID id, String eventType, String status, String description,
            String location, Instant createdAt) {
        public static TrackingEventDto from(TrackingEvent e) {
            return new TrackingEventDto(
                e.getId(), e.getEventType(),
                e.getStatus() == null ? null : e.getStatus().name(),
                e.getDescription(), e.getLocation(), e.getCreatedAt());
        }
    }

    public record LuggageDto(
            UUID id, String luggageNumber, UUID tripId, String tripNumber,
            String luggageType, BigDecimal capacityKg, BigDecimal assignedWeightKg,
            BigDecimal availableCapacityKg, BigDecimal actualWeightKg,
            String status, String sealNumber, String notes,
            UUID assignedAgentId, String assignedAgentName,
            UUID assignedTravelerId, String assignedTravelerName,
            List<ShipmentSummaryDto> shipments) {}

    public record ShipmentSummaryDto(
            UUID id, String trackingNumber, BigDecimal declaredWeightKg,
            String destinationCity, String status, String senderName, boolean hasPhoto,
            String recipientName, String recipientPhone, String description,
            List<RecipientDto> recipients, int photoCount) {
        public static ShipmentSummaryDto from(Shipment s) {
            return new ShipmentSummaryDto(s.getId(), s.getTrackingNumber(),
                    s.getDeclaredWeightKg(), s.getDestinationCity(),
                    s.getStatus() == null ? null : s.getStatus().name(),
                    s.senderDisplayName(),
                    s.getPhotoData() != null && !s.getPhotoData().isBlank(),
                    s.getRecipient().getName(), s.getRecipient().getPhone(), s.getDescription(),
                    s.allRecipients().stream().map(RecipientDto::from).toList(), s.allPhotos().size());
        }
    }

    public record TravelAnnouncementDto(
            UUID id, String postedByName, String origin, String destination,
            LocalDate departureDate, LocalDate expectedArrivalDate,
            String notes, java.time.Instant createdAt, UUID postedById,
            BigDecimal totalCapacityKg, Integer numberOfLuggage,
            BigDecimal approvedWeightKg, BigDecimal availableCapacityKg) {}

    public record TravelBookingRequestDto(
            UUID shipmentId, String trackingNumber, String senderName, BigDecimal weightKg,
            UUID travelAnnouncementId, String route, LocalDate departureDate, String status) {}

    public record TripDto(
            UUID id, String tripNumber, UUID travelerId, String travelerName,
            String departureAirport, String arrivalAirport,
            LocalDate departureDate, LocalDate expectedArrivalDate,
            BigDecimal totalLuggageCapacity, Integer numberOfLuggage,
            String status, String notes,
            BigDecimal assignedWeightKg, BigDecimal availableCapacityKg,
            List<LuggageDto> luggage, List<ShipmentSummaryDto> directShipments) {}

    public record PickupDto(
            UUID id, UUID shipmentId, String trackingNumber,
            String address, LocalDate date, String timeWindow,
            String status, String notes, Instant completedAt,
            Instant createdAt, Instant updatedAt) {}

    public record ReferralStatsDto(
            long totalReferrals, long activeShipments, long deliveredShipments,
            BigDecimal totalKg, BigDecimal totalRevenue,
            BigDecimal commissionEarned, BigDecimal commissionPaid, BigDecimal commissionPending,
            String referralCode) {}

    public record AdminDashboardDto(
            long totalShipments, long awaitingPickup, long atAlexandria,
            long readyForConsolidation, long assignedToLuggage,
            long activeTravelers, long activeTrips, long luggageInTransit,
            long arrivedInEthiopia, long pendingDeliveries,
            BigDecimal revenue, BigDecimal referralCommissions) {}
}
