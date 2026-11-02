package com.habeshlist.agent;

import com.habeshlist.agent.AgentTypes.CustomerType;
import com.habeshlist.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_customers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentCustomer {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User agent;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", unique = true)
    private User customer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private CustomerType customerType;
    @Column(nullable = false) private Instant createdAt;
}
