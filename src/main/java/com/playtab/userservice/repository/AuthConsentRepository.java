package com.playtab.userservice.repository;

import com.playtab.userservice.entity.AuthConsent;
import com.playtab.userservice.entity.enums.ConsentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AuthConsentRepository extends JpaRepository<AuthConsent, Long> {

    Optional<AuthConsent> findByIdentity_IdentityIdAndTypeAndTermsVersion(
            UUID identityId,
            ConsentType type,
            String termsVersion
    );

    @Query("""
        select c from AuthConsent c
        where c.identity.identityId = :identityId
          and c.type = :type
        order by c.consentId desc
    """)
    Page<AuthConsent> findLatestByType(UUID identityId, ConsentType type, Pageable pageable);

    @Query("""
        select case when count(c) > 0 then true else false end
        from AuthConsent c
        where c.identity.identityId = :identityId
          and c.type = :type
          and c.isAgreed = true
    """)
    boolean existsAgreedByIdentityAndType(UUID identityId, ConsentType type);
}
