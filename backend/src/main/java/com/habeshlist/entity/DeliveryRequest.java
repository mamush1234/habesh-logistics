package com.habeshlist.entity;

import com.habeshlist.entity.enums.MarketplaceTypes.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "delivery_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryRequest {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) private User sender;
    @Column(length = 120) private String guestName;
    @Column(length = 254) private String guestEmail;
    @Column(length = 40) private String guestPhone;
    @Column(columnDefinition = "text") private String productPhoto;
    @OneToOne(mappedBy = "sourceRequest", fetch = FetchType.LAZY) private Shipment convertedShipment;
    @Column(nullable = false, length = 100) private String pickupCity;
    @Column(nullable = false, length = 100) private String destinationCity;
    @Column(nullable = false) private LocalDate availableFrom;
    @Column(nullable = false) private LocalDate deadline;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ItemType itemType;
    @Column(nullable = false, length = 1000) private String description;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal weightKg;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal offeredPrice;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryStatus status;
    @OneToOne(fetch = FetchType.LAZY) private DeliveryOffer acceptedOffer;
    @Enumerated(EnumType.STRING) @Column(nullable = false) @Builder.Default
    private EscrowStatus escrowStatus = EscrowStatus.NONE;
    @Column(precision = 10, scale = 2) private BigDecimal escrowAmount;
    private Integer rating;
    @Column(length = 1000) private String disputeReason;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}
