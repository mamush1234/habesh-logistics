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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shipments", indexes = {
        @Index(name = "idx_shipments_tracking", columnList = "trackingNumber", unique = true),
        @Index(name = "idx_shipments_sender", columnList = "sender_id"),
        @Index(name = "idx_shipments_luggage", columnList = "luggage_id")
})
@EntityListeners(AuditingEntityListener.class)
@org.hibernate.annotations.DynamicUpdate
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_request_id", unique = true)
    private DeliveryRequest sourceRequest;

    public String senderDisplayName() {
        return sender == null ? "Guest sender" : sender.getFirstName() + " " + sender.getLastName();
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private Recipient recipient;

    @ManyToMany
    @JoinTable(name = "shipment_additional_recipients",
            joinColumns = @JoinColumn(name = "shipment_id"),
            inverseJoinColumns = @JoinColumn(name = "recipient_id"))
    @OrderColumn(name = "recipient_order")
    @Builder.Default
    private List<Recipient> additionalRecipients = new ArrayList<>();

    public List<Recipient> allRecipients() {
        List<Recipient> result = new ArrayList<>();
        result.add(recipient);
        result.addAll(additionalRecipients);
        return result;
    }

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

    @ElementCollection
    @CollectionTable(name = "shipment_additional_photos", joinColumns = @JoinColumn(name = "shipment_id"))
    @OrderColumn(name = "photo_order")
    @Column(name = "photo_data", columnDefinition = "text", nullable = false)
    @Builder.Default
    private List<String> additionalPhotos = new ArrayList<>();

    public List<String> allPhotos() {
        List<String> result = new ArrayList<>();
        if (photoData != null && !photoData.isBlank()) result.add(photoData);
        result.addAll(additionalPhotos);
        return result;
    }

    public void replacePhotos(List<String> photos) {
        photoData = photos.isEmpty() ? null : photos.get(0);
        additionalPhotos.clear();
        if (photos.size() > 1) additionalPhotos.addAll(photos.subList(1, photos.size()));
    }

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_announcement_id")
    private TravelAnnouncement travelAnnouncement;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private TravelBookingStatus travelBookingStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PackageQueueStatus queueStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_claimant_id")
    private User queueClaimant;

    private Instant queueExpiresAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal serviceFee;

    @Column(columnDefinition = "text")
    private String servicePaymentProof;

    private Instant servicePaymentUploadedAt;

    public Trip transportTrip() {
        return luggage == null ? trip : luggage.getTrip();
    }

    public boolean isCarriedBy(UUID travelerId) {
        Trip transport = transportTrip();
        return (assignedTraveler != null && assignedTraveler.getId().equals(travelerId))
                || (transport != null && transport.getTraveler().getId().equals(travelerId))
                || (luggage != null && luggage.getAssignedTraveler() != null
                    && luggage.getAssignedTraveler().getId().equals(travelerId));
    }

    @CreatedDate @Column(updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
