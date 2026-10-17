package com.habeshlist.dto.request;

import com.habeshlist.entity.enums.PickupMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class RequestDtos {
    private RequestDtos() {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Email @NotBlank String email,
            String phone,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotNull com.habeshlist.entity.enums.Role role
    ) {}

    public record UpdateUserAccessRequest(@NotNull Boolean active) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword) {}

    public record RecipientRequest(
            @NotBlank String name,
            @NotBlank String phone,
            @NotBlank String city,
            String address,
            String notes) {}

    public record CreateShipmentRequest(
            @Valid @NotNull RecipientRequest recipient,
            @NotBlank String destinationCountry,
            @NotBlank String destinationCity,
            String description,
            @NotNull @DecimalMin("0.1") @Digits(integer = 8, fraction = 2) BigDecimal declaredWeightKg,
            BigDecimal ratePerKg,
            @NotNull PickupMethod pickupMethod,
            String pickupAddress,
            LocalDate pickupRequestedDate,
            String pickupTimeWindow,
            String referralCode,
            java.util.UUID senderId, // Admin only: create on behalf of this sender
            com.habeshlist.entity.enums.PackageType packageType, // null = OTHER (priced per kg)
            String photoDataUrl,
            @Valid java.util.List<@NotNull RecipientRequest> additionalRecipients,
            @Size(max = 5) java.util.List<@NotBlank String> photoDataUrls,
            UUID travelAnnouncementId) {}

     public record UpdatePaymentRequest(@NotNull com.habeshlist.entity.enums.PaymentStatus paymentStatus) {}

     public record UpdatePhotoRequest(@NotBlank String photoDataUrl) {}

     public record UpdatePhotosRequest(
             @NotNull @Size(max = 5) java.util.List<@NotBlank String> photoDataUrls) {}

    public record UpdateStatusRequest(@NotBlank String status, String location, String description) {}

    public record CreateTripRequest(
            @NotNull java.util.UUID travelerId,
            @NotBlank String departureAirport,
            @NotBlank String arrivalAirport,
            @NotNull LocalDate departureDate,
            LocalDate expectedArrivalDate,
            @NotNull @DecimalMin("0.1") BigDecimal totalLuggageCapacity,
            @NotNull @Min(0) Integer numberOfLuggage,
            String notes) {}

    public record UpdateTripStatusRequest(@NotBlank String status) {}

    public record CreateTravelAnnouncementRequest(
            @NotBlank @Size(max = 100) String origin,
            @NotBlank @Size(max = 100) String destination,
            @NotNull @FutureOrPresent LocalDate departureDate,
            LocalDate expectedArrivalDate,
            @Size(max = 1000) String notes,
            @NotNull @DecimalMin("0.1") @Digits(integer = 8, fraction = 2) BigDecimal totalCapacityKg,
            @Min(0) Integer numberOfLuggage) {}

    public record UpdateTravelCapacityRequest(
            @NotNull @DecimalMin("0.1") @Digits(integer = 8, fraction = 2) BigDecimal totalCapacityKg,
            @Min(0) Integer numberOfLuggage) {}

    public record DecideTravelBookingRequest(@NotBlank @Pattern(regexp = "APPROVED|REJECTED") String status) {}

    public record CreateLuggageRequest(
            java.util.UUID tripId,
            String luggageType,
            @NotNull @DecimalMin("0.1") BigDecimal capacityKg,
            String sealNumber,
            String notes) {}

    public record UpdateLuggageStatusRequest(@NotBlank String status) {}

    public record AssignShipmentRequest(@NotNull java.util.UUID shipmentId) {}

    public record AssignShipmentsRequest(
            @NotEmpty @Size(max = 100) java.util.List<@NotNull UUID> shipmentIds) {}

    public record AssignTripRequest(java.util.UUID tripId) {}

    public record CreatePickupRequest(
            @NotNull java.util.UUID shipmentId,
            @NotBlank String address,
            @NotNull LocalDate date,
            String timeWindow,
            String notes) {}

    public record UpdatePickupStatusRequest(@NotBlank String status) {}

    public record CreateAgentRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Email @NotBlank String email,
            String phone,
            @NotBlank @Size(min = 12, max = 72) String password,
            String country,
            String city,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal commissionRate) {}

    public record AssignAgentRequest(java.util.UUID agentId) {} // null clears the agent

    public record AssignTravelerRequest(java.util.UUID travelerId) {} // null clears the traveler

    public record AdminCreateUserRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Email @NotBlank String email,
            String phone,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank String role) {}

    public record ScanRequest(@NotBlank String code, String action) {}
}
