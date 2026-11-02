package com.habeshlist.confirmation;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_confirmations")
@Getter @Setter @NoArgsConstructor
public class DeliveryConfirmation {
    @Id private UUID packageId;
    @Column(nullable = false) private String codeHash;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private int failedAttempts;
    @Column(nullable = false) private Instant issueWindowAt;
    @Column(nullable = false) private int issueCount;
    private Instant confirmedAt;
    private UUID confirmedBy;
}
