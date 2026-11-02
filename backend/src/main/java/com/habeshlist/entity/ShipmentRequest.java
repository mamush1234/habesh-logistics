package com.habeshlist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipment_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShipmentRequest {
    public enum Status { PENDING, ACCEPTED, DECLINED, EXPIRED }

    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "package_id", nullable = false)
    private DeliveryRequest packageRequest;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private MarketTrip trip;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User sender;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(unique = true) private UUID pendingPackageId;
    @Column(nullable = false, columnDefinition = "text") private String quoteJson;
    @Column(length = 500) private String message;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    @Column(columnDefinition = "text") private String proposedQuoteJson;
    @ManyToOne(fetch = FetchType.LAZY) private DeliveryOffer acceptedOffer;
    private Instant decidedAt;
}
