package com.playtab.userservice.service.user;

import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.AuthConsent;
import com.playtab.userservice.entity.AuthCredential;
import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.entity.UserSettings;
import com.playtab.userservice.entity.enums.ConsentType;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthCredentialRepository;
import com.playtab.userservice.repository.AuthIdentityRepository;
import com.playtab.userservice.repository.UserProfileRepository;
import com.playtab.userservice.service.auth.PasswordService;
import com.playtab.userservice.service.user.email.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSignupService {

    private final AuthIdentityRepository identityRepo;
    private final AuthCredentialRepository credentialRepo;
    private final UserProfileRepository profileRepo;

    private final PasswordService passwordService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public UUID signUpWithEmail(SignupCommand cmd) {
        if (cmd == null) {
            throw new DomainException(ErrorCode.INVALID_REQUEST);
        }

        String email = normalizeEmail(cmd.email());
        if (email == null) {
            throw new DomainException(ErrorCode.INVALID_EMAIL);
        }

        validatePassword(cmd.password());

        if (credentialRepo.existsByTypeAndIdentifier(CredentialType.EMAIL, email)) {
            throw new DomainException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (profileRepo.findByEmail(email).isPresent()) {
            throw new DomainException(ErrorCode.DUPLICATE_EMAIL);
        }

        emailVerificationService.assertVerifiedOrThrow(email, cmd.sessionId());

        List<SignupCommand.Consent> consents = (cmd.consents() == null)
                ? List.of()
                : cmd.consents();

        validateRequiredConsents(consents);

        AuthIdentity identity = new AuthIdentity();

        UserProfile profile = new UserProfile();
        profile.setEmail(email);
        profile.setName(blankToNull(cmd.name()));
        profile.setGender(cmd.gender());
        profile.setPhoneNumber(blankToNull(cmd.phoneNumber()));
        profile.setBirthDate(cmd.birthDate());
        profile.setNationality(resolveNationality(cmd.nationality()));
        identity.attachProfile(profile);

        UserSettings settings = new UserSettings();
        identity.attachSettings(settings);

        AuthCredential emailCred = new AuthCredential();
        emailCred.setType(CredentialType.EMAIL);
        emailCred.setIdentifier(email);
        emailCred.setPasswordHash(passwordService.hash(cmd.password()));
        emailCred.setIsPrimary(true);
        identity.addCredential(emailCred);

        Instant now = Instant.now();
        Map<ConsentKey, SignupCommand.Consent> deduped = dedupeConsents(consents);

        for (var entry : deduped.entrySet()) {
            ConsentKey key = entry.getKey();
            SignupCommand.Consent in = entry.getValue();

            AuthConsent consent = new AuthConsent();
            consent.setType(key.type);
            consent.setTermsVersion(key.termsVersion);
            consent.setIsAgreed(Boolean.TRUE.equals(in.isAgreed()));
            consent.setAgreedAt(Boolean.TRUE.equals(in.isAgreed()) ? now : null);

            identity.addConsent(consent);
        }

        AuthIdentity saved = identityRepo.save(identity);

        registerConsumeVerifiedAfterCommit(email, cmd.sessionId());

        return saved.getIdentityId();
    }

    private void registerConsumeVerifiedAfterCommit(String email, String sessionId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailVerificationService.consumeVerified(email, sessionId);
                }
            });
        } else {
            emailVerificationService.consumeVerified(email, sessionId);
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_REQUEST);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String resolveNationality(String nationality) {
        String normalized = blankToNull(nationality);
        return normalized == null ? "KR" : normalized;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private void validateRequiredConsents(List<SignupCommand.Consent> consents) {
        boolean serviceAgreed = consents.stream().anyMatch(c ->
                c != null
                        && c.type() == ConsentType.SERVICE
                        && Boolean.TRUE.equals(c.isAgreed())
                        && c.termsVersion() != null
                        && !c.termsVersion().isBlank()
        );

        boolean privacyAgreed = consents.stream().anyMatch(c ->
                c != null
                        && c.type() == ConsentType.PRIVACY
                        && Boolean.TRUE.equals(c.isAgreed())
                        && c.termsVersion() != null
                        && !c.termsVersion().isBlank()
        );

        if (!serviceAgreed || !privacyAgreed) {
            throw new DomainException(ErrorCode.REQUIRED_CONSENT_MISSING);
        }
    }

    private Map<ConsentKey, SignupCommand.Consent> dedupeConsents(List<SignupCommand.Consent> consents) {
        Map<ConsentKey, SignupCommand.Consent> map = new LinkedHashMap<>();

        for (SignupCommand.Consent c : consents) {
            if (c == null) continue;

            if (c.type() == null) {
                throw new DomainException(ErrorCode.CONSENT_TYPE_UNSPECIFIED);
            }

            if (c.termsVersion() == null || c.termsVersion().isBlank()) {
                throw new DomainException(ErrorCode.TERMS_VERSION_REQUIRED);
            }

            map.put(new ConsentKey(c.type(), c.termsVersion()), c);
        }

        return map;
    }

    private static final class ConsentKey {
        private final ConsentType type;
        private final String termsVersion;

        private ConsentKey(ConsentType type, String termsVersion) {
            this.type = type;
            this.termsVersion = termsVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConsentKey other)) return false;
            return type == other.type && Objects.equals(termsVersion, other.termsVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, termsVersion);
        }
    }
}