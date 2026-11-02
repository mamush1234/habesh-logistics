package com.habeshlist.entity;

import com.habeshlist.entity.enums.MarketplaceTypes.ItemType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "trip_item_prices", uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "category"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TripItemPrice {
    public enum PriceUnit { PER_KG, PER_ITEM }

    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false) private MarketTrip trip;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ItemType category;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PriceUnit unit;
}
