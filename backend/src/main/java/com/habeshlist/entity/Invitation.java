package com.habeshlist.entity;

import com.habeshlist.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Invitation {
    @Id @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;
}
