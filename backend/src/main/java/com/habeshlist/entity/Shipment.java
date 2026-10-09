package com.habeshlist.entity;

import com.habeshlist.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shipments", indexes = {
        @Index(name = "idx_shipments_tracking", columnList = "trackingNumber", unique = true),
        @Index(name = "idx_shipments_sender", columnList = "sender_id"),
        @Index(name = "idx_shipments_luggage", columnList = "luggage_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Shipment {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String trackingNumber;

    /** Opaque token for public tracking links. */
    @Column(nullable = false, unique = true, length = 64)
    private String trackingToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private Recipient recipient;

    @Column(nullable = false)
    private String destinationCountry;
    @Column(nullable = false)
    private String destinationCity;

    @Column(length = 1000)
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal declaredWeightKg;
    @Column(precision = 10, scale = 2)
    private BigDecimal actualWeightKg;
    @Column(precision = 10, scale = 2)
    private BigDecimal ratePerKg;
    @Column(precision = 10, scale = 2)
    private BigDecimal shippingFee;
    @Column(precision = 10, scale = 2)
    private BigDecimal pickupFee;
    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PackageType packageType = PackageType.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PickupMethod pickupMethod;

    private String pickupAddress;
    private LocalDate pickupRequestedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @Column(columnDefinition = "text")
    private String photoData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referring_agent_id")
    private User referringAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private User assignedAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_traveler_id")
    private User assignedTraveler;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "luggage_id")
    private Luggage luggage;

    @CreatedDate @Column(updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
