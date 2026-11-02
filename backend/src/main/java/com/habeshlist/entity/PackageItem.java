package com.habeshlist.entity;

import com.habeshlist.entity.enums.MarketplaceTypes.ItemType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "package_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PackageItem {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false) private DeliveryRequest request;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ItemType category;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal weightKg;
    @Column(nullable = false, length = 1000) private String description;
}
