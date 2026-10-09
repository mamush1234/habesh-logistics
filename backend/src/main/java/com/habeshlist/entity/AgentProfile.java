package com.habeshlist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "agent_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentProfile {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private String country;
    private String city;

    @Column(unique = true)
    private String referralCode;

    @Column(precision = 5, scale = 4)
    private BigDecimal commissionRate;

    private boolean active = true;
}
