package com.playtab.userservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "identity_id", nullable = false)
    private AuthIdentity identity;

    @Column(name = "refresh_token_hash", nullable = false, length = 256)
    private String refreshTokenHash;

    @Column(name = "device_fingerprint", length = 256)
    private String deviceFingerprint;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_revoked")
    private Boolean isRevoked = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (sessionId == null) sessionId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (isRevoked == null) isRevoked = false;
    }
}
