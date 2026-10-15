package com.habeshlist.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "travel_announcements")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TravelAnnouncement {
    @Id @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posted_by_id", nullable = false)
    private User postedBy;

    @Column(nullable = false, length = 100)
    private String origin;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(nullable = false)
    private LocalDate departureDate;

    private LocalDate expectedArrivalDate;

    @Column(length = 1000)
    private String notes;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalCapacityKg;

    private Integer numberOfLuggage;

    @CreatedDate @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
