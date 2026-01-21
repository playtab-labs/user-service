package com.playtab.userservice.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.playtab.userservice.entity.enums.CredentialType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(
        name = "auth_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_credentials_type_identifier",
                columnNames = {"type", "identifier"}
        )
)
public class AuthCredential {

    @Id
    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "identity_id", nullable = false)
    private AuthIdentity identity;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CredentialType type;

    @Column(name = "identifier", nullable = false, length = 255)
    private String identifier;

    @Column(name = "password_hash", length = 512)
    private String passwordHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_meta", columnDefinition = "jsonb")
    private JsonNode externalMeta;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (credentialId == null) credentialId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (isPrimary == null) isPrimary = false;
    }
}
