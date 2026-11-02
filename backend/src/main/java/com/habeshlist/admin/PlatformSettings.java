package com.habeshlist.admin;

import com.habeshlist.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "platform_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformSettings {
    public static final short SINGLETON_ID = 1;

    public enum FeePayer { SENDER, TRAVELER, SPLIT }

    @Id private Short id;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal platformFeePercent;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal agentCommissionPercent;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private FeePayer feePayer;
    @Column(nullable = false) private Instant updatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
