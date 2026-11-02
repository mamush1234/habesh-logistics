package com.habeshlist.dto;

import com.habeshlist.admin.PlatformSettings.FeePayer;
import com.habeshlist.entity.ShipmentRequest.Status;
import com.habeshlist.entity.TripItemPrice.PriceUnit;
import com.habeshlist.entity.enums.MarketplaceTypes.ItemType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ShipmentRequestDtos {
    private ShipmentRequestDtos() {}
    public record LineDto(ItemType category, BigDecimal weightKg, BigDecimal rate, PriceUnit unit, BigDecimal amount) {}
    public record QuoteDto(UUID packageId, UUID tripId, String origin, String destination, LocalDate departureDate,
            List<LineDto> items, BigDecimal packageAmount, BigDecimal platformFeePercent, FeePayer feePayer,
            BigDecimal platformFee, BigDecimal senderFee, BigDecimal travelerFee, BigDecimal total,
            BigDecimal travelerPayout, String quoteKey) {}
    public record SubmitInput(@NotNull UUID packageId, @NotNull UUID tripId,
            @NotBlank @Size(min = 64, max = 64) String quoteKey, @Size(max = 500) String message) {}
    public record RequestDto(UUID id, UUID packageId, UUID tripId, Status status, QuoteDto quote,
            String message, Instant createdAt, Instant expiresAt, boolean adminManaged,
            QuoteDto proposedQuote, Instant decidedAt, boolean canReview, boolean canRespond) {}
    public enum Action { ACCEPT, DECLINE, PROPOSE_PRICE, CONFIRM_PRICE, REJECT_PRICE }
    public record DecisionInput(@NotNull Action action,
            @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal proposedPrice,
            @Size(min = 64, max = 64) String quoteKey) {}
}
