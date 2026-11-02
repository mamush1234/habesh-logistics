package com.habeshlist.agent;

import com.habeshlist.agent.AgentTypes.AuthorizationMethod;
import com.habeshlist.agent.AgentTypes.CustomerType;
import com.habeshlist.dto.MarketplaceDtos.RequestInput;
import com.habeshlist.entity.enums.CommissionStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.ItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AgentDtos {
    private AgentDtos() {}

    public record AuthorizationGrant(@AssertTrue(message = "Confirm the customer authorized you to act on their behalf")
            boolean confirmed, @NotNull AuthorizationMethod method, @Size(max = 500) String consentNote,
            @Min(1) @Max(365) Integer validDays) {}
    public record AuthorizationInput(@NotNull UUID customerId, @NotNull AuthorizationMethod method,
            @Size(max = 500) String consentNote, @Min(1) @Max(365) Integer validDays) {}
    public record CreateCustomerInput(@NotNull CustomerType customerType,
            @NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 40) String phone,
            @NotNull @Valid AuthorizationGrant authorization) {}
    public record PackageInput(@NotNull @Valid RequestInput details,
            @Size(max = 3_000_000) String photoDataUrl, boolean draft) {}
    public record PromoteInput(@DecimalMin("0.0") @DecimalMax("1.0") BigDecimal commissionRate) {}

    public record AuthorizationDto(UUID id, UUID customerId, String customerName, String status,
            AuthorizationMethod method, String consentNote, Instant grantedAt, Instant expiresAt, Instant revokedAt) {}
    public record CustomerDto(UUID id, String name, String email, String phone, CustomerType type, boolean active,
            boolean mustChangePassword, long shipmentCount, long tripCount, AuthorizationDto authorization,
            Instant createdAt) {}
    public record Credentials(String email, String temporaryPassword) {}
    public record CustomerCreatedDto(CustomerDto customer, Credentials credentials, AuthorizationDto authorization) {}
    public record PackageDto(UUID id, UUID customerId, String customerName, String pickupCity, String destinationCity,
            LocalDate availableFrom, LocalDate deadline, ItemType itemType, String description, BigDecimal weightKg,
            BigDecimal offeredPrice, DeliveryStatus status, boolean adminApproved, boolean hasPhoto,
            Instant createdAt, Instant updatedAt,
            List<com.habeshlist.dto.MarketplaceDtos.PackageItemDto> items,
            com.habeshlist.dto.MarketplaceDtos.ReceiverInput receiver) {}
    public record TripDto(UUID id, UUID customerId, String customerName, String origin, String destination,
            LocalDate departureDate, LocalDate expectedArrivalDate, BigDecimal capacityKg, BigDecimal availableKg,
            Set<ItemType> allowedItems, Set<ItemType> blockedItems, Instant createdAt,
            String handoffLocation, String notes, List<com.habeshlist.dto.MarketplaceDtos.TripItemPriceDto> itemPrices) {}
    public record ActivityDto(UUID id, String action, String customerName, String targetType, UUID targetId,
            String details, Instant createdAt) {}
    public record DashboardDto(String agentName, String email, String referralCode, BigDecimal commissionRate,
            long activeCustomers, long activeShipments, long pendingRequests, BigDecimal totalCommissions,
            List<ActivityDto> recentActivity, List<CustomerDto> recentCustomers) {}
    public record CommissionDto(UUID id, UUID requestId, String route, String customerName, BigDecimal rate,
            BigDecimal baseAmount, BigDecimal amount, CommissionStatus status, Instant createdAt, Instant paidAt) {}
    public record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
    public record CommissionPageDto(List<CommissionDto> items, int page, int size, long totalItems, int totalPages,
            BigDecimal total, BigDecimal pending, BigDecimal paid) {}
    public record AgentSummaryDto(UUID userId, String name, String email, String referralCode,
            BigDecimal commissionRate, boolean active) {}
}
