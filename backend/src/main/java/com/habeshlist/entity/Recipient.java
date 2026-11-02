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
    @Column(nullable = false, length = 64)
    private String phone;
    @Column(nullable = false, length = 128)
    private String city;
    @Column(length = 500)
    private String address;
    @Column(length = 1000)
    private String notes;
}
