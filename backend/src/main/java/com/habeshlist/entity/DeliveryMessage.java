package com.habeshlist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "sequence_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryMessage {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private DeliveryRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User author;
    @Column(nullable = false, length = 2000) private String body;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private long sequenceNumber;
}
