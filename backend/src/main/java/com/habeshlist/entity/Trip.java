package com.habeshlist.entity;

import com.habeshlist.entity.enums.TripStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trips", indexes = {
        @Index(name = "idx_trips_number", columnList = "tripNumber", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Trip {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String tripNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "traveler_id")
    private User traveler;

    @Column(nullable = false, length = 8)
    private String departureAirport;
    @Column(nullable = false, length = 8)
    private String arrivalAirport;

    @Column(nullable = false)
    private LocalDate departureDate;
    private LocalDate expectedArrivalDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalLuggageCapacity;

    private Integer numberOfLuggage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Luggage> luggageList = new ArrayList<>();

    @CreatedDate @Column(updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
