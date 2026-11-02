package com.habeshlist.agent;

import com.habeshlist.entity.DeliveryRequest;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.CommissionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_commissions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentCommission {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User agent;
    @ManyToOne(fetch = FetchType.LAZY) private User customer;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "request_id", unique = true)
    private DeliveryRequest request;
    @Column(nullable = false, precision = 5, scale = 4) private BigDecimal rate;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal baseAmount;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private CommissionStatus status;
    @Column(nullable = false) private Instant createdAt;
    private Instant paidAt;
}
