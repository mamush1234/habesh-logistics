package com.habeshlist.review;

import com.habeshlist.entity.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "delivery_reviews", uniqueConstraints = {
        @UniqueConstraint(name = "uk_delivery_review_direction", columnNames = {"package_id", "direction"}),
        @UniqueConstraint(name = "uk_delivery_review_author", columnNames = {"package_id", "author_id"})})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryReview {
    public enum Direction { SENDER_TO_CARRIER, CARRIER_TO_SENDER }
    public enum Criterion { COMMUNICATION, ON_TIME_DELIVERY, PACKAGE_HANDLING, RELIABILITY,
        ACCURATE_DESCRIPTION, HANDOFF_TIMELINESS, PACKAGING_QUALITY }
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "package_id", nullable = false)
    private DeliveryRequest packageRequest;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User author;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User recipient;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Direction direction;
    @Column(nullable = false) private int rating;
    @Column(length = 2000) private String comment;
    @Column(nullable = false) private boolean legacy;
    @Column(nullable = false) private Instant createdAt;
    @ElementCollection
    @CollectionTable(name = "delivery_review_scores", joinColumns = @JoinColumn(name = "review_id"))
    @MapKeyColumn(name = "criterion", length = 32) @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "score", nullable = false) @Builder.Default
    private Map<Criterion, Integer> scores = new EnumMap<>(Criterion.class);
}
