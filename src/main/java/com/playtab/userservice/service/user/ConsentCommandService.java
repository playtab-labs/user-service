package com.playtab.userservice.service.user;

import com.playtab.userservice.entity.AuthConsent;
import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.enums.ConsentType;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.proto.v1.ConsentInput;
import com.playtab.userservice.repository.AuthConsentRepository;
import com.playtab.userservice.repository.AuthIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsentCommandService {

    private final AuthIdentityRepository identityRepo;
    private final AuthConsentRepository consentRepo;

    @Transactional
    public void upsertConsents(UUID identityId, java.util.List<ConsentInput> inputs) {

        AuthIdentity identity = identityRepo.findById(identityId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));

        Instant now = Instant.now();

        for (var in : inputs) {

            String termsVersion = in.getTermsVersion();
            if (termsVersion == null || termsVersion.isBlank()) {
                throw new DomainException(ErrorCode.TERMS_VERSION_REQUIRED);
            }

            ConsentType type = mapConsentType(in.getType());

            boolean agreed = in.getIsAgreed();

            // ✅ 필수 약관은 false로 변경 불가
            if ((type == ConsentType.SERVICE || type == ConsentType.PRIVACY) && !agreed) {
                throw new DomainException(ErrorCode.REQUIRED_CONSENT_MISSING);
            }

            AuthConsent consent = consentRepo
                    .findByIdentity_IdentityIdAndTypeAndTermsVersion(identityId, type, termsVersion)
                    .orElseGet(() -> {
                        AuthConsent c = new AuthConsent();
                        c.setIdentity(identity);
                        c.setTermsVersion(termsVersion);
                        c.setType(type);
                        return c;
                    });

            consent.setIsAgreed(agreed);
            consent.setAgreedAt(agreed ? now : null); // 너 정책 유지

            consentRepo.save(consent);
        }
    }

    private ConsentType mapConsentType(com.playtab.userservice.proto.v1.ConsentType t) {
        return switch (t) {
            case PRIVACY -> ConsentType.PRIVACY;
            case SERVICE -> ConsentType.SERVICE;
            case MARKETING -> ConsentType.MARKETING;
            case CONSENT_TYPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new DomainException(ErrorCode.CONSENT_TYPE_UNSPECIFIED);
        };
    }
}
