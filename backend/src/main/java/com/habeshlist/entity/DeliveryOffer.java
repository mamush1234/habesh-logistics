package com.habeshlist.entity;

import com.habeshlist.entity.enums.MarketplaceTypes.OfferStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_offers", uniqueConstraints =
        @UniqueConstraint(name = "uk_delivery_offer_traveler", columnNames = {"request_id", "traveler_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryOffer {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private DeliveryRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private MarketTrip trip;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User traveler;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal price;
    @Column(length = 500) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OfferStatus status;
    @Column(nullable = false) private Instant createdAt;
}
