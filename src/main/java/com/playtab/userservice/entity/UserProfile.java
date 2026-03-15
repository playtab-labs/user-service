package com.playtab.userservice.entity;

import com.playtab.userservice.entity.enums.Gender;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(
        name = "user_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_profiles_email", columnNames = "email")
        },
        indexes = {
                @Index(name = "idx_user_profiles_email", columnList = "email")
        }
)
public class UserProfile {

    @Id
    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "identity_id", nullable = false, unique = true)
    private AuthIdentity identity;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "name", length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender = Gender.UNSPECIFIED;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "nationality", length = 10)
    private String nationality;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name="is_adult")
    private Boolean isAdult;

    @Column(name="updated_at")
    private Instant updatedAt;

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (profileId == null) profileId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (gender == null) gender = Gender.UNSPECIFIED;
    }
}
