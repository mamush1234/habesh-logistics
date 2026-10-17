package com.habeshlist.entity;

import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryEvent {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private DeliveryRequest request;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryStatus status;
    @Column(nullable = false) private UUID actorId;
    @Column(nullable = false, length = 1000) private String description;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private long sequenceNumber;
}
