package com.nhom6.motofix.entity;
import lombok.*;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Column(nullable = false, unique = true, length = 30)
    private String name;
}