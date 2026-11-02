package com.habeshlist.admin;

import com.habeshlist.entity.enums.CommissionStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.EscrowStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.ItemType;
import com.habeshlist.entity.enums.Role;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    /** Paged list; {@code counts} carries per-tab/per-status totals for the unpaged result. */
    public record ListDto<T>(List<T> items, int page, int size, long totalItems, int totalPages, Map<String, Long> counts) {}

    public record ChartPointDto(LocalDate date, BigDecimal revenue, long bookings) {}
    public record ActivityDto(String type, String title, String detail, Instant at) {}
    public record OverviewDto(LocalDate from, LocalDate to, String bucket, BigDecimal platformFeePercent,
            BigDecimal totalRevenue, BigDecimal platformFees, long activeShipments, long pendingReviews,
            long totalBookings, List<ChartPointDto> chart, List<ActivityDto> activities) {}

    public record UserRowDto(UUID id, String name, String email, String phone, Role role, String status,
            boolean active, Instant createdAt, long shipments, long trips) {}

    public record TripRowDto(UUID id, UUID travelerId, String travelerName, String travelerEmail, String origin,
            String destination, LocalDate departureDate, LocalDate expectedArrivalDate, BigDecimal capacityKg,
            BigDecimal reservedKg, long offers, String status, boolean agentCreated, Instant createdAt) {}

    public record RequestRowDto(UUID id, String senderName, String senderEmail, String pickupCity,
            String destinationCity, ItemType itemType, String description, BigDecimal weightKg,
            BigDecimal offeredPrice, DeliveryStatus status, boolean adminApproved, EscrowStatus escrowStatus,
            BigDecimal escrowAmount, String travelerName, BigDecimal acceptedPrice, long offers, Integer rating,
            String disputeReason, boolean guest, boolean agentCreated, LocalDate deadline, String lastEvent,
            Instant lastEventAt, Instant createdAt, Instant updatedAt) {}

    public record PaymentRowDto(UUID requestId, String senderName, String travelerName, String route,
            BigDecimal amount, BigDecimal platformFee, BigDecimal travelerPayout, EscrowStatus escrowStatus,
            DeliveryStatus status, Instant fundedAt) {}
    public record PaymentSummaryDto(BigDecimal held, BigDecimal released, BigDecimal refunded, BigDecimal platformFees) {}
    public record PaymentsDto(PaymentSummaryDto summary, ListDto<PaymentRowDto> page) {}

    public record CommissionRowDto(UUID id, String agentName, String customerName, UUID requestId, String route,
            BigDecimal rate, BigDecimal baseAmount, BigDecimal amount, CommissionStatus status, Instant createdAt,
            Instant paidAt) {}

    public record RouteCountDto(String route, long requests, BigDecimal value) {}
    public record ReportDto(LocalDate from, LocalDate to, Map<String, Long> requestsByStatus,
            Map<String, Long> usersByRole, List<RouteCountDto> topRoutes, BigDecimal averageRating, long ratings) {}

    public record ResolveDisputeInput(@NotNull DeliveryStatus outcome, @NotBlank @Size(max = 1000) String reason) {}

    public record SettingsDto(BigDecimal platformFeePercent, BigDecimal agentCommissionPercent,
            PlatformSettings.FeePayer feePayer, Instant updatedAt, String updatedBy) {}
    public record SettingsInput(
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal platformFeePercent,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal agentCommissionPercent,
            @NotNull PlatformSettings.FeePayer feePayer) {}
}
