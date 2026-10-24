package com.habeshlist.dto;

import com.habeshlist.entity.enums.MarketplaceTypes.*;
import com.habeshlist.entity.enums.Role;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class MarketplaceDtos {
    private MarketplaceDtos() {}
    public record TripInput(
            @NotBlank @Size(max = 100) String origin, @NotBlank @Size(max = 100) String destination,
            @NotNull @FutureOrPresent LocalDate departureDate, @FutureOrPresent LocalDate expectedArrivalDate,
            @NotNull @DecimalMin("0.1") @Digits(integer = 8, fraction = 2) BigDecimal capacityKg,
            @NotEmpty Set<@NotNull ItemType> allowedItems, @NotNull Set<@NotNull ItemType> blockedItems) {}
    public record RequestInput(
            @NotBlank @Size(max = 100) String pickupCity, @NotBlank @Size(max = 100) String destinationCity,
            @NotNull @FutureOrPresent LocalDate availableFrom, @NotNull @FutureOrPresent LocalDate deadline,
            @NotNull ItemType itemType, @NotBlank @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.1") @Digits(integer = 8, fraction = 2) BigDecimal weightKg,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal offeredPrice) {}
    public record OfferInput(@NotNull UUID tripId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @Size(max = 500) String message) {}
    public record TransitionInput(@NotNull DeliveryStatus status, @Size(max = 1000) String reason) {}
    public record RatingInput(@NotNull @Min(1) @Max(5) Integer rating) {}
    public record VerificationInput(@NotNull VerificationLevel level) {}
    public record TravelerDto(UUID id, String name, Double rating, long ratingCount, VerificationLevel verification, Role role) {}
    public record TripDto(UUID id, TravelerDto traveler, String origin, String destination, LocalDate departureDate,
            LocalDate expectedArrivalDate, BigDecimal capacityKg, BigDecimal availableKg,
            Set<ItemType> allowedItems, Set<ItemType> blockedItems) {}
    public record OfferDto(UUID id, UUID tripId, TravelerDto traveler, BigDecimal price, String message,
            OfferStatus status, double score) {}
    public record ContactDto(String name, String email, String phone) {}
    public record EventDto(DeliveryStatus status, String description, Instant createdAt) {}
    public record RequestDto(UUID id, UUID senderId, String pickupCity, String destinationCity,
            LocalDate availableFrom, LocalDate deadline, ItemType itemType, String description,
            BigDecimal weightKg, BigDecimal offeredPrice, DeliveryStatus status, EscrowStatus escrowStatus,
            BigDecimal escrowAmount, UUID acceptedOfferId, Integer rating, String disputeReason,
            List<OfferDto> offers, List<EventDto> timeline, ContactDto senderContact, ContactDto travelerContact,
            Instant createdAt, String senderName, boolean guestRequest, boolean hasProductPhoto,
            UUID convertedShipmentId, String shipmentTrackingNumber) {}
    public record MatchDto(RequestDto request, TripDto trip, BigDecimal price, double score) {}
    public record MessageInput(@NotBlank @Size(max = 2000) String body) {}
    public record MessageDto(UUID id, UUID authorId, String authorName, Role authorRole,
            String body, Instant createdAt, long sequenceNumber) {}
    public record ChatDto(List<MessageDto> messages, Long nextBefore, boolean writable) {}
}
