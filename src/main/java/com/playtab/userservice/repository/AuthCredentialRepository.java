package com.playtab.userservice.repository;

import com.playtab.userservice.entity.AuthCredential;
import com.playtab.userservice.entity.enums.CredentialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthCredentialRepository extends JpaRepository<AuthCredential, UUID> {
    Optional<AuthCredential> findByTypeAndIdentifier(CredentialType type, String identifier);
    boolean existsByTypeAndIdentifier(CredentialType type, String identifier);

    Optional<AuthCredential> findByIdentity_IdentityIdAndType(UUID identityId, CredentialType type);
}