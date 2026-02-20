package com.playtab.userservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(
        name = "user_settings",
        indexes = {
                @Index(name = "idx_user_settings_identity", columnList = "identity_id")
        }
)
public class UserSettings {

    @Id
    @Column(name = "settings_id", nullable = false)
    private UUID settingsId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "identity_id", nullable = false, unique = true)
    private AuthIdentity identity;

    @Column(name = "locale", nullable = false, length = 20)
    private String locale = "ko-KR";

    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = true;

    @Column(name = "email_notifications_enabled", nullable = false)
    private Boolean emailNotificationsEnabled = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (settingsId == null) settingsId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (locale == null || locale.isBlank()) locale = "ko-KR";
        if (pushEnabled == null) pushEnabled = true;
        if (emailNotificationsEnabled == null) emailNotificationsEnabled = true;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
