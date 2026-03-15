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
import com.playtab.userservice.repository.AuthConsentRepository;
import com.playtab.userservice.service.auth.PasswordService;
import com.playtab.userservice.service.user.email.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSignupService {

    private final AuthIdentityRepository identityRepo;
    private final AuthCredentialRepository credentialRepo;
    private final AuthConsentRepository consentRepo;

    private final PasswordService passwordService;
    private final EmailVerificationService emailVerificationService;

    /**
     * 회원가입(Email)
     * - 이메일 중복 체크 (auth_credentials: (EMAIL, email))
     * - 이메일 인증 확인 (sessionId 기반)
     * - identity + profile + settings + credential + consents 생성
     * - SERVICE/PRIVACY 필수 동의 검증
     */
    @Transactional
    public UUID signUpWithEmail(SignupCommand cmd) {
        if (cmd == null) throw new DomainException(ErrorCode.INVALID_REQUEST);

        String email = normalizeEmail(cmd.email());
        if (email == null) throw new DomainException(ErrorCode.INVALID_EMAIL);

        if (cmd.password() == null || cmd.password().isBlank()) {
            throw new DomainException(ErrorCode.INVALID_REQUEST);
        }

        // 1) 이메일 중복 체크 (EMAIL credential 기준)
        if (credentialRepo.existsByTypeAndIdentifier(CredentialType.EMAIL, email)) {
            throw new DomainException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 2) 이메일 인증 확인 (프로젝트 메서드명에 맞게 바꿔)
        // 예) emailVerificationService.assertVerified(email, cmd.sessionId());
        // 혹은 boolean ok = emailVerificationService.isVerified(email, cmd.sessionId());
        // if (!ok) throw new DomainException(ErrorCode.EMAIL_NOT_VERIFIED);

        // 3) consents 입력 정리 + 필수 동의 검증
        List<SignupCommand.Consent> consents = (cmd.consents() == null)
                ? List.of()
                : cmd.consents();

        validateRequiredConsents(consents);  // SERVICE/PRIVACY = true 필수

        // 4) Identity 생성
        AuthIdentity identity = new AuthIdentity();
        // identityId/createdAt는 @PrePersist가 채우지만, 명확히 해도 됨
        // identity.setIdentityId(UUID.randomUUID());

        // 5) Profile 생성 (개인정보)
        UserProfile profile = new UserProfile();
        profile.setEmail(email);
        profile.setName(cmd.name());
        profile.setGender(cmd.gender());          // cmd.gender()가 entity Gender라고 가정(네 mapper가 그렇게 만듦)
        profile.setPhoneNumber(cmd.phoneNumber());
        profile.setBirthDate(cmd.birthDate());
        profile.setNationality(cmd.nationality() == null ? "KR" : cmd.nationality());
        // isAdult는 VerifyAdult에서 갱신하니까 여기선 null/false로 두어도 됨

        identity.attachProfile(profile);

        // 6) Settings 생성 (언어/알림 토글)
        UserSettings settings = new UserSettings();
        // 기본 locale/push/email = 엔티티 default로 OK
        identity.attachSettings(settings);

        // 7) Credential 생성 (EMAIL)
        AuthCredential emailCred = new AuthCredential();
        emailCred.setType(CredentialType.EMAIL);
        emailCred.setIdentifier(email);
        emailCred.setPasswordHash(passwordService.hash(cmd.password()));
        emailCred.setIsPrimary(true);
        identity.addCredential(emailCred);

        // 8) Consents upsert 저장
        // - 동일 (type, termsVersion)이 들어오면 마지막 값으로 반영
        // - agreed_at: true => now, false => null (현재 비동의 명확)
        Instant now = Instant.now();
        Map<ConsentKey, SignupCommand.Consent> deduped = dedupeConsents(consents);

        for (var entry : deduped.entrySet()) {
            ConsentKey key = entry.getKey();
            SignupCommand.Consent in = entry.getValue();

            if (key.termsVersion == null || key.termsVersion.isBlank()) {
                throw new DomainException(ErrorCode.TERMS_VERSION_REQUIRED);
            }

            AuthConsent c = new AuthConsent();
            c.setType(key.type);
            c.setTermsVersion(key.termsVersion);
            c.setIsAgreed(Boolean.TRUE.equals(in.isAgreed()));
            c.setAgreedAt(Boolean.TRUE.equals(in.isAgreed()) ? now : null);

            identity.addConsent(c);
        }

        // 9) 저장 (cascade로 profile/settings/credential/consents 함께 저장)
        AuthIdentity saved = identityRepo.save(identity);
        return saved.getIdentityId();
    }

    // -------------------------
    // Helpers
    // -------------------------

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase(Locale.ROOT);
        if (e.isBlank()) return null;
        // 엄격한 정규식 검증은 네 기존 INVALID_EMAIL 정책에 맞게 별도 적용 가능
        return e;
    }

    private void validateRequiredConsents(List<SignupCommand.Consent> consents) {
        // SERVICE/PRIVACY는 반드시 true
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

        // MARKETING은 있어도 되고 없어도 됨(있다면 termsVersion 필수는 아래 dedupe에서 처리)
    }

    private Map<ConsentKey, SignupCommand.Consent> dedupeConsents(List<SignupCommand.Consent> consents) {
        // 같은 (type, termsVersion)이 여러 번 들어오면 마지막 값을 채택
        Map<ConsentKey, SignupCommand.Consent> map = new LinkedHashMap<>();
        for (SignupCommand.Consent c : consents) {
            if (c == null) continue;
            if (c.type() == null) throw new DomainException(ErrorCode.CONSENT_TYPE_UNSPECIFIED);
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

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConsentKey other)) return false;
            return type == other.type && Objects.equals(termsVersion, other.termsVersion);
        }

        @Override public int hashCode() {
            return Objects.hash(type, termsVersion);
        }
    }
}
