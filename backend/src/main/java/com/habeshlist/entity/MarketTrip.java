package com.habeshlist.entity;

import com.habeshlist.entity.enums.MarketplaceTypes.ItemType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "market_trips")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MarketTrip {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User traveler;
    @Column(nullable = false, length = 100) private String origin;
    @Column(nullable = false, length = 100) private String destination;
    @Column(nullable = false) private LocalDate departureDate;
    private LocalDate expectedArrivalDate;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal capacityKg;
    @Column(nullable = false, precision = 10, scale = 2) @Builder.Default
    private BigDecimal reservedKg = BigDecimal.ZERO;
    @ElementCollection @CollectionTable(name = "market_trip_allowed", joinColumns = @JoinColumn(name = "trip_id"))
    @Enumerated(EnumType.STRING) @Column(name = "item_type", nullable = false)
    @Builder.Default private Set<ItemType> allowedItems = new HashSet<>();
    @ElementCollection @CollectionTable(name = "market_trip_blocked", joinColumns = @JoinColumn(name = "trip_id"))
    @Enumerated(EnumType.STRING) @Column(name = "item_type", nullable = false)
    @Builder.Default private Set<ItemType> blockedItems = new HashSet<>();
    @Column(nullable = false) private Instant createdAt;
}
