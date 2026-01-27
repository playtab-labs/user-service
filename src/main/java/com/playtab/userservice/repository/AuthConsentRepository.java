package com.playtab.userservice.repository;

import com.playtab.userservice.entity.AuthConsent;
import com.playtab.userservice.entity.enums.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthConsentRepository extends JpaRepository<AuthConsent, Long> {

    Optional<AuthConsent> findByIdentity_IdentityIdAndTypeAndTermsVersion(
            UUID identityId,
            com.playtab.userservice.entity.enums.ConsentType type,
            String termsVersion
    );
}