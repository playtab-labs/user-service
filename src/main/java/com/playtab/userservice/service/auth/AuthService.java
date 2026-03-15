package com.playtab.userservice.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.playtab.userservice.dto.auth.*;
import com.playtab.userservice.entity.*;
import com.playtab.userservice.entity.enums.ConsentType;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.entity.enums.IdentityStatus;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthConsentRepository;
import com.playtab.userservice.repository.AuthCredentialRepository;
import com.playtab.userservice.repository.AuthIdentityRepository;
import com.playtab.userservice.repository.UserProfileRepository;
import com.playtab.userservice.service.auth.social.SocialAuthProviderRegistry;
import com.playtab.userservice.service.auth.social.SocialUserInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AuthService {

    private final AuthCredentialRepository credentialRepo;
    private final AuthIdentityRepository identityRepo;
    private final UserProfileRepository profileRepo;
    private final AuthConsentRepository consentRepo;

    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final SessionService sessionService;

    private final SocialAuthProviderRegistry socialRegistry;
    private final ObjectMapper om = new ObjectMapper();

    public AuthService(
            AuthCredentialRepository credentialRepo,
            AuthIdentityRepository identityRepo,
            UserProfileRepository profileRepo,
            AuthConsentRepository consentRepo,
            PasswordService passwordService,
            TokenService tokenService,
            SessionService sessionService,
            SocialAuthProviderRegistry socialRegistry
    ) {
        this.credentialRepo = credentialRepo;
        this.identityRepo = identityRepo;
        this.profileRepo = profileRepo;
        this.consentRepo = consentRepo;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.socialRegistry = socialRegistry;
    }

    // ---------------------------
    // Email Login
    // ---------------------------
    @Transactional
    public AuthTokens loginWithEmail(LoginCommand cmd) {
        AuthCredential cred = credentialRepo.findByTypeAndIdentifier(CredentialType.EMAIL, normalizeEmail(cmd.email()))
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_FAILED));

        AuthIdentity identity = cred.getIdentity();
        assertIdentityActiveOrThrow(identity);

        if (!passwordService.matches(cmd.password(), cred.getPasswordHash())) {
            throw new DomainException(ErrorCode.AUTH_FAILED);
        }

        return issueTokensAndPersistSession(identity, cmd.deviceFingerprint(), cmd.userAgent(), cmd.ipAddress());
    }

    // ---------------------------
    // Social Login
    // - 신규가입(또는 required consents 미존재)일 경우: consents를 반드시 받아서 저장
    // ---------------------------
    @Transactional
    public AuthTokens loginWithSocial(SocialLoginCommand cmd) {
        if (cmd == null || cmd.type() == null) throw new DomainException(ErrorCode.INVALID_REQUEST);
        if (cmd.type() == CredentialType.EMAIL) throw new DomainException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);

        SocialUserInfo info = socialRegistry.get(cmd.type()).verifyAndGetUserInfo(cmd);
        if (info == null || blank(info.providerUserId())) throw new DomainException(ErrorCode.INVALID_SOCIAL_TOKEN);
        if (blank(info.email())) throw new DomainException(ErrorCode.SOCIAL_EMAIL_REQUIRED);

        String normalizedEmail = normalizeEmail(info.email());

        // 1) provider credential(sub 등)로 기존 계정 찾기
        Optional<AuthCredential> byProvider = credentialRepo.findByTypeAndIdentifier(cmd.type(), info.providerUserId());
        AuthIdentity identity = byProvider
                .map(AuthCredential::getIdentity)
                .orElseGet(() -> resolveOrCreateIdentityAndLink(cmd.type(), info, cmd.consents()));

        assertIdentityActiveOrThrow(identity);

        // 2) 토큰 발급 + refresh session 저장
        return issueTokensAndPersistSession(identity, cmd.deviceFingerprint(), cmd.userAgent(), cmd.ipAddress());
    }

    /**
     * provider credential이 없는 경우:
     *  - (A) EMAIL credential로 기존 계정 찾기
     *  - (B) user_profiles.email로도 기존 계정 찾기 (중복 identity 방지)
     *  - 없으면 신규 identity 생성(+settings, +필수 consents 저장 강제)
     */
    private AuthIdentity resolveOrCreateIdentityAndLink(
            CredentialType provider,
            SocialUserInfo info,
            List<com.playtab.userservice.proto.v1.ConsentInput> consentsFromClient
    ) {
        String email = normalizeEmail(info.email());

        // (A) EMAIL credential 기준
        Optional<AuthIdentity> byEmailCred = credentialRepo.findByTypeAndIdentifier(CredentialType.EMAIL, email)
                .map(AuthCredential::getIdentity);

        // (B) PROFILE email 기준 (소셜 가입자도 매칭)
        Optional<AuthIdentity> byProfileEmail = profileRepo.findByEmail(email)
                .map(UserProfile::getIdentity);

        AuthIdentity identity = byEmailCred.or(() -> byProfileEmail)
                .orElseGet(() -> createNewIdentityWithSocial(provider, info, consentsFromClient));

        // 여기부터는 “기존 identity에 소셜 credential 연결”
        boolean alreadyLinked = identity.getCredentials().stream()
                .anyMatch(c -> c.getType() == provider && info.providerUserId().equals(c.getIdentifier()));

        if (!alreadyLinked) {
            AuthCredential socialCred = new AuthCredential();
            socialCred.setType(provider);
            socialCred.setIdentifier(info.providerUserId());
            socialCred.setIsPrimary(false);
            socialCred.setExternalMeta(buildExternalMetaMinimal(info));
            identity.addCredential(socialCred);
        }

        // profile email 보정 (비어있으면 채움)
        profileRepo.findByIdentity_IdentityId(identity.getIdentityId()).ifPresent(p -> {
            if (blank(p.getEmail())) {
                p.setEmail(email);
                profileRepo.save(p);
            }
        });

        // ✅ required consent가 없으면, 첫 소셜 로그인 시점에라도 반드시 받아서 저장(정책)
        ensureRequiredConsents(identity, consentsFromClient);

        return identityRepo.save(identity);
    }

    private AuthIdentity createNewIdentityWithSocial(
            CredentialType provider,
            SocialUserInfo info,
            List<com.playtab.userservice.proto.v1.ConsentInput> consentsFromClient
    ) {
        // ✅ 신규 소셜 가입은 필수 약관 동의가 필요
        validateRequiredConsentsOrThrow(consentsFromClient);

        AuthIdentity identity = new AuthIdentity();

        // 1) Social Credential
        AuthCredential socialCred = new AuthCredential();
        socialCred.setType(provider);
        socialCred.setIdentifier(info.providerUserId());
        socialCred.setIsPrimary(true);
        socialCred.setExternalMeta(buildExternalMetaMinimal(info));
        identity.addCredential(socialCred);

        // 2) Profile (email만 최소)
        UserProfile profile = new UserProfile();
        profile.setEmail(normalizeEmail(info.email()));
        profile.setName(null);
        identity.attachProfile(profile);

        // 3) Settings: EMAIL 가입과 동일하게 “가입 시점에 생성”
        UserSettings settings = new UserSettings();
        identity.attachSettings(settings);

        // 4) Consents: 가입 시점에 생성(필수 포함)
        upsertConsentsForIdentity(identity, consentsFromClient);

        return identityRepo.save(identity);
    }

    private void ensureRequiredConsents(AuthIdentity identity, List<com.playtab.userservice.proto.v1.ConsentInput> consentsFromClient) {
        boolean hasRequired = hasRequiredConsents(identity.getIdentityId());

        if (hasRequired) return;

        // required가 없는데 입력도 없으면 정책 위반
        validateRequiredConsentsOrThrow(consentsFromClient);

        // 기존 identity에도 동의 저장(업서트)
        upsertConsentsByRepo(identity.getIdentityId(), consentsFromClient);
    }

    private boolean hasRequiredConsents(UUID identityId) {
        // 간단 체크: SERVICE/PRIVACY 각각 agreed=true 1개 이상 존재 여부
        boolean service = consentRepo.existsAgreedByIdentityAndType(identityId, ConsentType.SERVICE);
        boolean privacy = consentRepo.existsAgreedByIdentityAndType(identityId, ConsentType.PRIVACY);
        return service && privacy;
    }

    private void validateRequiredConsentsOrThrow(List<com.playtab.userservice.proto.v1.ConsentInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new DomainException(ErrorCode.REQUIRED_CONSENT_MISSING);
        }
        boolean service = inputs.stream().anyMatch(c ->
                mapConsentType(c.getType()) == ConsentType.SERVICE
                        && c.getIsAgreed()
                        && !blank(c.getTermsVersion())
        );
        boolean privacy = inputs.stream().anyMatch(c ->
                mapConsentType(c.getType()) == ConsentType.PRIVACY
                        && c.getIsAgreed()
                        && !blank(c.getTermsVersion())
        );
        if (!service || !privacy) throw new DomainException(ErrorCode.REQUIRED_CONSENT_MISSING);
    }

    private void upsertConsentsForIdentity(AuthIdentity identity, List<com.playtab.userservice.proto.v1.ConsentInput> inputs) {
        Instant now = Instant.now();
        Map<ConsentKey, com.playtab.userservice.proto.v1.ConsentInput> deduped = dedupe(inputs);

        for (var entry : deduped.entrySet()) {
            ConsentKey key = entry.getKey();
            var in = entry.getValue();

            AuthConsent c = new AuthConsent();
            c.setType(key.type);
            c.setTermsVersion(key.termsVersion);
            c.setIsAgreed(in.getIsAgreed());
            c.setAgreedAt(in.getIsAgreed() ? now : null);

            identity.addConsent(c);
        }
    }

    private void upsertConsentsByRepo(UUID identityId, List<com.playtab.userservice.proto.v1.ConsentInput> inputs) {
        Instant now = Instant.now();
        Map<ConsentKey, com.playtab.userservice.proto.v1.ConsentInput> deduped = dedupe(inputs);

        for (var entry : deduped.entrySet()) {
            ConsentKey key = entry.getKey();
            var in = entry.getValue();

            // (identityId, type, termsVersion) 기준 업서트
            AuthConsent consent = consentRepo
                    .findByIdentity_IdentityIdAndTypeAndTermsVersion(identityId, key.type, key.termsVersion)
                    .orElseGet(() -> {
                        AuthConsent c = new AuthConsent();
                        // identity는 lazy라서 repo save 전에 FK만 맞으면 됨, 하지만 깔끔하게 reference를 얻어도 됨
                        AuthIdentity ref = identityRepo.getReferenceById(identityId);
                        c.setIdentity(ref);
                        c.setType(key.type);
                        c.setTermsVersion(key.termsVersion);
                        return c;
                    });

            // 필수 약관은 false로 변경 불가(정책)
            if ((key.type == ConsentType.SERVICE || key.type == ConsentType.PRIVACY) && !in.getIsAgreed()) {
                throw new DomainException(ErrorCode.REQUIRED_CONSENT_MISSING);
            }

            consent.setIsAgreed(in.getIsAgreed());
            consent.setAgreedAt(in.getIsAgreed() ? now : null);
            consentRepo.save(consent);
        }
    }

    private Map<ConsentKey, com.playtab.userservice.proto.v1.ConsentInput> dedupe(List<com.playtab.userservice.proto.v1.ConsentInput> inputs) {
        Map<ConsentKey, com.playtab.userservice.proto.v1.ConsentInput> map = new LinkedHashMap<>();
        if (inputs == null) return map;

        for (var c : inputs) {
            if (c == null) continue;
            ConsentType type = mapConsentType(c.getType());
            String v = c.getTermsVersion();
            if (blank(v)) throw new DomainException(ErrorCode.TERMS_VERSION_REQUIRED);
            map.put(new ConsentKey(type, v), c);
        }
        return map;
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

    private ObjectNode buildExternalMetaMinimal(SocialUserInfo info) {
        ObjectNode meta = om.createObjectNode();
        meta.put("providerUserId", info.providerUserId());
        if (info.email() != null) meta.put("email", info.email());
        if (info.emailVerified() != null) meta.put("emailVerified", info.emailVerified());
        return meta;
    }

    // ---------------------------
    // Refresh
    // ---------------------------
    @Transactional
    public AuthTokens refreshTokens(RefreshCommand cmd) {
        AuthSession session = sessionService.findByRefreshTokenForUpdate(cmd.refreshToken())
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN));

        sessionService.validateActiveSessionOrThrow(session);

        if (cmd.deviceFingerprint() != null && session.getDeviceFingerprint() != null) {
            if (!cmd.deviceFingerprint().equals(session.getDeviceFingerprint())) {
                throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
            }
        }

        AuthIdentity identity = session.getIdentity();
        assertIdentityActiveOrThrow(identity);

        UUID newSessionId = UUID.randomUUID();
        String newAccess = tokenService.issueAccessToken(identity.getIdentityId(), identity.getRole());
        String newRefresh = tokenService.issueRefreshToken(identity.getIdentityId(), newSessionId);

        Instant newRefreshExp = Instant.now().plusSeconds(tokenService.refreshTtlSeconds());

        sessionService.rotate(
                identity,
                session,
                newRefresh,
                cmd.deviceFingerprint(),
                cmd.userAgent(),
                cmd.ipAddress(),
                newRefreshExp
        );

        boolean profileCompleted = profileRepo.findByIdentity_IdentityId(identity.getIdentityId())
                .map(this::isProfileCompleted)
                .orElse(false);

        return AuthTokens.builder()
                .accessToken(newAccess)
                .refreshToken(newRefresh)
                .accessExpiresInSeconds(tokenService.accessTtlSeconds())
                .refreshExpiresInSeconds(tokenService.refreshTtlSeconds())
                .profileCompleted(profileCompleted)
                .build();
    }

    // ---------------------------
    // Logout
    // ---------------------------
    @Transactional
    public void logout(LogoutCommand cmd) {
        AuthSession session = sessionService.findByRefreshTokenForUpdate(cmd.refreshToken())
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN));

        sessionService.revoke(session);
    }

    // ---------------------------
    // Common Helpers
    // ---------------------------
    private AuthTokens issueTokensAndPersistSession(AuthIdentity identity,
                                                    String deviceFingerprint,
                                                    String userAgent,
                                                    String ipAddress) {
        UUID sessionId = UUID.randomUUID();
        String access = tokenService.issueAccessToken(identity.getIdentityId(), identity.getRole());
        String refresh = tokenService.issueRefreshToken(identity.getIdentityId(), sessionId);

        Instant refreshExp = Instant.now().plusSeconds(tokenService.refreshTtlSeconds());

        sessionService.create(identity, refresh, deviceFingerprint, userAgent, ipAddress, refreshExp);

        boolean profileCompleted = profileRepo.findByIdentity_IdentityId(identity.getIdentityId())
                .map(this::isProfileCompleted)
                .orElse(false);

        return AuthTokens.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .accessExpiresInSeconds(tokenService.accessTtlSeconds())
                .refreshExpiresInSeconds(tokenService.refreshTtlSeconds())
                .profileCompleted(profileCompleted)
                .build();
    }

    private void assertIdentityActiveOrThrow(AuthIdentity identity) {
        if (identity == null) throw new DomainException(ErrorCode.AUTH_FAILED);

        if (identity.getStatus() == IdentityStatus.DELETED) {
            throw new DomainException(ErrorCode.ACCOUNT_DELETED);
        }
        if (identity.getStatus() == IdentityStatus.LOCKED) {
            throw new DomainException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    private boolean isProfileCompleted(UserProfile p) {
        return p.getEmail() != null && !p.getEmail().isBlank()
                && p.getName() != null && !p.getName().isBlank();
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase(Locale.ROOT);
        return e.isBlank() ? null : e;
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }
}
