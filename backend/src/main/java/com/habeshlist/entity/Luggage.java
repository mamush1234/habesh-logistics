package com.habeshlist.entity;

import com.habeshlist.entity.enums.LuggageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "luggage", indexes = {
        @Index(name = "idx_luggage_number", columnList = "luggageNumber", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Luggage {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String luggageNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    private String luggageType;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal capacityKg;

    @Column(precision = 10, scale = 2)
    private BigDecimal actualWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LuggageStatus status;

    private String qrCode;
    private String sealNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private User assignedAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_traveler_id")
    private User assignedTraveler;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "luggage")
    @Builder.Default
    private List<Shipment> shipments = new ArrayList<>();

    @CreatedDate @Column(updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
