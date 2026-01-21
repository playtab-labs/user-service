package com.playtab.userservice.entity;

import com.playtab.userservice.entity.enums.IdentityStatus;
import com.playtab.userservice.entity.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "auth_identities")
public class AuthIdentity {

    @Id
    @Column(name = "identity_id", nullable = false)
    private UUID identityId;

    @Column(name = "ci_hash", length = 256, unique = true)
    private String ciHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private IdentityStatus status = IdentityStatus.ACTIVE;

    @Column(name = "created_at")
    private Instant createdAt;

    // ✅ Profile: user_profiles.identity_id FK가 있다고 가정
    @OneToOne(mappedBy = "identity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserProfile profile;

    // ✅ Credentials: auth_credentials.identity_id FK
    @OneToMany(mappedBy = "identity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuthCredential> credentials = new ArrayList<>();

    // ✅ Consents: auth_consents.identity_id FK
    @OneToMany(mappedBy = "identity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuthConsent> consents = new ArrayList<>();

    // ---------- 편의 메서드(핵심) ----------
    public void attachProfile(UserProfile profile) {
        this.profile = profile;
        profile.setIdentity(this);
    }

    public void addCredential(AuthCredential credential) {
        this.credentials.add(credential);
        credential.setIdentity(this);
    }

    public void addConsent(AuthConsent consent) {
        this.consents.add(consent);
        consent.setIdentity(this);
    }

    @PrePersist
    void prePersist() {
        if (identityId == null) identityId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (role == null) role = Role.USER;
        if (status == null) status = IdentityStatus.ACTIVE;
    }
}
