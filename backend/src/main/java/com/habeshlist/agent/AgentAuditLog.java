package com.habeshlist.agent;

import com.habeshlist.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentAuditLog {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User agent;
    @ManyToOne(fetch = FetchType.LAZY) private User customer;
    @ManyToOne(fetch = FetchType.LAZY) private AgentAuthorization authorization;
    @Column(nullable = false, length = 48) private String action;
    @Column(length = 32) private String targetType;
    private UUID targetId;
    @Column(length = 500) private String details;
    @Column(nullable = false) private Instant createdAt;
}
