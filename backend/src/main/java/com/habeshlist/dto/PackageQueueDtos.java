package com.habeshlist.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PackageQueueDtos {
    private PackageQueueDtos() {}

    public record PackageDto(UUID id, String trackingNumber, String destinationCountry, String destinationCity,
                             String description, String packageType, BigDecimal weightKg, BigDecimal shipmentPrice,
                             BigDecimal serviceFee, String shipmentStatus, String queueStatus,
                             UUID claimantId, String claimantName, Instant expiresAt, Instant serverTime,
                             Instant createdAt, Instant paymentUploadedAt, List<String> photos) {}

    public record SenderContactDto(String name, String email, String phone, String pickupAddress) {}
    public record NotificationDto(UUID id, UUID shipmentId, UUID requestId, String message, Instant createdAt, Instant readAt) {}
}
