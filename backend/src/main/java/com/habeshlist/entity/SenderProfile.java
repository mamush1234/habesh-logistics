package com.habeshlist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "sender_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SenderProfile {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private String preferredContactMethod;
    private String address;
}
