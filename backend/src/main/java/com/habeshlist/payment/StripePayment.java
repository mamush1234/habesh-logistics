package com.habeshlist.payment;

import com.habeshlist.entity.DeliveryRequest;
import com.habeshlist.entity.ShipmentRequest;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stripe_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StripePayment {
    public enum Status { PENDING, SUCCEEDED, CANCELLED, REFUNDED }
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "package_id", nullable = false, unique = true)
    private DeliveryRequest packageRequest;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "shipment_request_id", nullable = false, unique = true)
    private ShipmentRequest shipmentRequest;
    @Column(unique = true) private String intentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private long amountCents;
    @Column(nullable = false, columnDefinition = "text") private String quoteJson;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}
