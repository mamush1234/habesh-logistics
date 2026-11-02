package com.habeshlist.review;

import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.review.DeliveryReview.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ReviewDtos {
    private ReviewDtos() {}
    public record Input(@NotNull @Min(1) @Max(5) @JsonDeserialize(using = WholeScore.class) Integer rating,
            @JsonDeserialize(contentUsing = WholeScore.class)
            @NotNull @Size(min = 4, max = 4) Map<@NotNull Criterion, @NotNull @Min(1) @Max(5) Integer> scores,
            @Size(max = 2000) String comment) {}
    public static final class WholeScore extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws java.io.IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
                throw JsonMappingException.from(parser, "Review scores must be whole numbers");
            return parser.getIntValue();
        }
    }
    public record ReviewDto(UUID id, Direction direction, int rating, Map<Criterion, Integer> scores,
            String comment, boolean legacy, Instant createdAt, boolean mine) {}
    public record BookingDto(UUID packageId, String origin, String destination, DeliveryStatus status,
            boolean canReview, Direction direction, List<ReviewDto> reviews) {}
    public record InboxDto(List<BookingDto> items, int page, int totalPages, long totalElements) {}
}
