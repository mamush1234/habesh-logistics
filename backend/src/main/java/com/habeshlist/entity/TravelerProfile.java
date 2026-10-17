package com.habeshlist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "traveler_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TravelerProfile {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private String verificationStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private com.habeshlist.entity.enums.MarketplaceTypes.VerificationLevel marketplaceVerification =
            com.habeshlist.entity.enums.MarketplaceTypes.VerificationLevel.UNVERIFIED;
    private String notes;
}
