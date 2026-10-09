package com.habeshlist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "recipients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Recipient {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String phone;
    @Column(nullable = false)
    private String city;
    private String address;
    private String notes;
}
