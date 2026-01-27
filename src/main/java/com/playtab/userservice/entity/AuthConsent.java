package com.playtab.userservice.entity;

import com.playtab.userservice.entity.enums.ConsentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "auth_consents")
public class AuthConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id", nullable = false)
    private Long consentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "identity_id", nullable = false)
    private AuthIdentity identity;

    @Column(name = "terms_version", nullable = false, length = 50)
    private String termsVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ConsentType type;

    @Column(name = "is_agreed", nullable = false)
    private Boolean isAgreed;

    @Column(name = "agreed_at")
    private Instant agreedAt;

    @PrePersist
    void prePersist() {
        if (agreedAt == null) agreedAt = Instant.now();
        if (isAgreed == null) isAgreed = false;
    }
}
