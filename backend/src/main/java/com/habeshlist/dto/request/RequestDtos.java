package com.habeshlist.dto.request;

import com.habeshlist.entity.enums.PickupMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

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
            @NotBlank @Size(min = 6) String password,
            String role // Optional: SENDER (default) or TRAVELER.
    ) {}

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
            @NotNull @DecimalMin("0.1") BigDecimal declaredWeightKg,
            BigDecimal ratePerKg,
            @NotNull PickupMethod pickupMethod,
            String pickupAddress,
            LocalDate pickupRequestedDate,
            String pickupTimeWindow,
            String referralCode,
            java.util.UUID senderId, // Admin only: create on behalf of this sender
            String guestFirstName, String guestLastName, String guestEmail, String guestPhone, // Guest checkout only
            com.habeshlist.entity.enums.PackageType packageType, // null = OTHER (priced per kg)
            String photoDataUrl) {} // optional package picture as a data:image/... URL

     public record UpdatePaymentRequest(@NotNull com.habeshlist.entity.enums.PaymentStatus paymentStatus) {}

    public record UpdateStatusRequest(@NotBlank String status, String location, String description) {}

    public record CreateTripRequest(
            @NotNull java.util.UUID travelerId,
            @NotBlank String departureAirport,
            @NotBlank String arrivalAirport,
            @NotNull LocalDate departureDate,
            LocalDate expectedArrivalDate,
            @NotNull @DecimalMin("0.1") BigDecimal totalLuggageCapacity,
            @NotNull @Min(1) Integer numberOfLuggage,
            String notes) {}

    public record UpdateTripStatusRequest(@NotBlank String status) {}

    public record CreateLuggageRequest(
            @NotNull java.util.UUID tripId,
            String luggageType,
            @NotNull @DecimalMin("0.1") BigDecimal capacityKg,
            String sealNumber,
            String notes) {}

    public record UpdateLuggageStatusRequest(@NotBlank String status) {}

    public record AssignShipmentRequest(@NotNull java.util.UUID shipmentId) {}

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
            @NotBlank @Size(min = 6) String password,
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
            @NotBlank @Size(min = 6) String password,
            @NotBlank String role) {}

    public record ScanRequest(@NotBlank String code, String action) {}
}
