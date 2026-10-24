package com.habeshlist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guest_request_links")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GuestRequestLink {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false, length = 120) private String label;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User createdBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant revokedAt;
    @OneToOne(fetch = FetchType.LAZY) private DeliveryRequest request;
}
