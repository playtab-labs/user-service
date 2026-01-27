package com.playtab.userservice.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.playtab.userservice.dto.auth.*;
import com.playtab.userservice.entity.*;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.entity.enums.IdentityStatus;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthCredentialRepository;
import com.playtab.userservice.repository.AuthIdentityRepository;
import com.playtab.userservice.repository.UserProfileRepository;
import com.playtab.userservice.service.auth.social.SocialAuthProviderRegistry;
import com.playtab.userservice.service.auth.social.SocialUserInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthCredentialRepository credentialRepo;
    private final AuthIdentityRepository identityRepo;
    private final UserProfileRepository profileRepo;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final SessionService sessionService;

    // Social
    private final SocialAuthProviderRegistry socialRegistry;
    private final ObjectMapper om = new ObjectMapper();

    public AuthService(
            AuthCredentialRepository credentialRepo,
            AuthIdentityRepository identityRepo,
            UserProfileRepository profileRepo,
            PasswordService passwordService,
            TokenService tokenService,
            SessionService sessionService,
            SocialAuthProviderRegistry socialRegistry
    ) {
        this.credentialRepo = credentialRepo;
        this.identityRepo = identityRepo;
        this.profileRepo = profileRepo;
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
        AuthCredential cred = credentialRepo.findByTypeAndIdentifier(CredentialType.EMAIL, cmd.email())
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_FAILED));

        AuthIdentity identity = cred.getIdentity();
        assertIdentityActiveOrThrow(identity);

        if (!passwordService.matches(cmd.password(), cred.getPasswordHash())) {
            throw new DomainException(ErrorCode.AUTH_FAILED);
        }

        return issueTokensAndPersistSession(identity, cmd.deviceFingerprint(), cmd.userAgent(), cmd.ipAddress());
    }

    // ---------------------------
    // Social Login (email만 최소 생성, name/gender는 UI에서)
    // ---------------------------
    @Transactional
    public AuthTokens loginWithSocial(SocialLoginCommand cmd) {
        if (cmd == null || cmd.type() == null) {
            throw new DomainException(ErrorCode.INVALID_REQUEST);
        }
        if (cmd.type() == CredentialType.EMAIL) {
            throw new DomainException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }

        SocialUserInfo info = socialRegistry.get(cmd.type()).verifyAndGetUserInfo(cmd);

        if (info == null || info.providerUserId() == null || info.providerUserId().isBlank()) {
            throw new DomainException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }

        if (info.email() == null || info.email().isBlank()) {
            throw new DomainException(ErrorCode.SOCIAL_EMAIL_REQUIRED);
        }

        // 1) provider credential(sub 등)로 기존 계정 찾기
        AuthIdentity identity = credentialRepo.findByTypeAndIdentifier(cmd.type(), info.providerUserId())
                .map(AuthCredential::getIdentity)
                .orElseGet(() -> resolveOrCreateIdentityAndLink(cmd.type(), info));

        assertIdentityActiveOrThrow(identity);

        // 2) 토큰 발급 + refresh session 저장
        return issueTokensAndPersistSession(identity, cmd.deviceFingerprint(), cmd.userAgent(), cmd.ipAddress());
    }

    /**
     * provider credential이 없는 경우:
     *  - 이메일로 기존 EMAIL 계정이 있으면 해당 identity에 소셜 credential 연결
     *  - 없으면 신규 identity 생성 + 소셜 credential + profile(email만 최소 생성)
     */
    private AuthIdentity resolveOrCreateIdentityAndLink(CredentialType provider, SocialUserInfo info) {

        // 1) 이메일(EMAIL credential) 기준으로 기존 계정 찾기
        AuthIdentity identity = credentialRepo.findByTypeAndIdentifier(CredentialType.EMAIL, info.email())
                .map(AuthCredential::getIdentity)
                .orElseGet(() -> createNewIdentityWithSocial(provider, info));

        // 2) 해당 identity에 소셜 credential이 없으면 연결
        boolean alreadyLinked = identity.getCredentials().stream()
                .anyMatch(c -> c.getType() == provider && info.providerUserId().equals(c.getIdentifier()));

        if (!alreadyLinked) {
            AuthCredential socialCred = new AuthCredential();
            socialCred.setType(provider);
            socialCred.setIdentifier(info.providerUserId());
            socialCred.setIsPrimary(false);
            socialCred.setExternalMeta(buildExternalMetaMinimal(info));

            identity.addCredential(socialCred);
            identity = identityRepo.save(identity);
        }

        // 3) profile 최소 보정: email 비어있으면 채우기만
        profileRepo.findByIdentity_IdentityId(identity.getIdentityId()).ifPresent(p -> {
            if (p.getEmail() == null || p.getEmail().isBlank()) {
                p.setEmail(info.email());
                profileRepo.save(p);
            }
        });

        return identity;
    }

    private AuthIdentity createNewIdentityWithSocial(CredentialType provider, SocialUserInfo info) {
        AuthIdentity identity = new AuthIdentity();
        identity.setIdentityId(UUID.randomUUID());

        AuthCredential socialCred = new AuthCredential();
        socialCred.setType(provider);
        socialCred.setIdentifier(info.providerUserId());
        socialCred.setIsPrimary(true);
        socialCred.setExternalMeta(buildExternalMetaMinimal(info));
        identity.addCredential(socialCred);

        UserProfile profile = new UserProfile();
        profile.setEmail(info.email());
        profile.setName(null); // 명시적으로 null
        identity.attachProfile(profile);

        return identityRepo.save(identity);
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
}