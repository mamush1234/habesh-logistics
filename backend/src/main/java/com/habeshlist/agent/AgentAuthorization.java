package com.habeshlist.agent;

import com.habeshlist.agent.AgentTypes.AuthorizationMethod;
import com.habeshlist.agent.AgentTypes.AuthorizationStatus;
import com.habeshlist.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_authorizations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentAuthorization {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User agent;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User customer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AuthorizationStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AuthorizationMethod method;
    @Column(length = 500) private String consentNote;
    @Column(nullable = false) private Instant grantedAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant revokedAt;

    public boolean activeAt(Instant now) {
        return status == AuthorizationStatus.ACTIVE && expiresAt.isAfter(now);
    }
}
