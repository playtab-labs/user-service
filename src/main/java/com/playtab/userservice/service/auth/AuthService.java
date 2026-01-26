package com.playtab.userservice.service.auth;

import com.playtab.userservice.dto.auth.AuthTokens;
import com.playtab.userservice.dto.auth.LoginCommand;
import com.playtab.userservice.dto.auth.LogoutCommand;
import com.playtab.userservice.dto.auth.RefreshCommand;
import com.playtab.userservice.entity.AuthCredential;
import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.AuthSession;
import com.playtab.userservice.entity.UserProfile;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.entity.enums.IdentityStatus;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthCredentialRepository;
import com.playtab.userservice.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthCredentialRepository credentialRepo;
    private final UserProfileRepository profileRepo;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final SessionService sessionService;

    public AuthService(
            AuthCredentialRepository credentialRepo,
            UserProfileRepository profileRepo,
            PasswordService passwordService,
            TokenService tokenService,
            SessionService sessionService
    ) {
        this.credentialRepo = credentialRepo;
        this.profileRepo = profileRepo;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
    }

    @Transactional
    public AuthTokens loginWithEmail(LoginCommand cmd) {
        AuthCredential cred = credentialRepo.findByTypeAndIdentifier(CredentialType.EMAIL, cmd.email())
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_FAILED));

        AuthIdentity identity = cred.getIdentity();
        if (identity.getStatus() == IdentityStatus.DELETED) {
            throw new DomainException(ErrorCode.ACCOUNT_DELETED);
        }
        if (identity.getStatus() == IdentityStatus.LOCKED) {
            throw new DomainException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordService.matches(cmd.password(), cred.getPasswordHash())) {
            // Step4에서 실패횟수/락 처리(LoginAttemptService) 붙이면 됨
            throw new DomainException(ErrorCode.AUTH_FAILED);
        }

        UUID sessionId = UUID.randomUUID();
        String access = tokenService.issueAccessToken(identity.getIdentityId(), identity.getRole());
        String refresh = tokenService.issueRefreshToken(identity.getIdentityId(), sessionId);

        Instant refreshExp = Instant.now().plusSeconds(tokenService.refreshTtlSeconds());

        // refresh_token_hash 저장(원문 저장 금지)
        sessionService.create(
                identity,
                refresh,
                cmd.deviceFingerprint(),
                cmd.userAgent(),
                cmd.ipAddress(),
                refreshExp
        );

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

    @Transactional
    public AuthTokens refreshTokens(RefreshCommand cmd) {
        // 1) DB에서 refresh hash로 세션을 "잠금 조회"
        AuthSession session = sessionService.findByRefreshTokenForUpdate(cmd.refreshToken())
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN));

        sessionService.validateActiveSessionOrThrow(session);

        // (선택) 디바이스 검증 강화: deviceFingerprint가 다르면 거절
        if (cmd.deviceFingerprint() != null && session.getDeviceFingerprint() != null) {
            if (!cmd.deviceFingerprint().equals(session.getDeviceFingerprint())) {
                throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
            }
        }

        AuthIdentity identity = session.getIdentity();

        // 2) 새 토큰 발급(ROTATION)
        UUID newSessionId = UUID.randomUUID();
        String newAccess = tokenService.issueAccessToken(identity.getIdentityId(), identity.getRole());
        String newRefresh = tokenService.issueRefreshToken(identity.getIdentityId(), newSessionId);

        Instant newRefreshExp = Instant.now().plusSeconds(tokenService.refreshTtlSeconds());

        // 3) 이전 세션 revoke + 새 세션 insert
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

    @Transactional
    public void logout(LogoutCommand cmd) {
        AuthSession session = sessionService.findByRefreshTokenForUpdate(cmd.refreshToken())
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN));

        // idempotent
        sessionService.revoke(session);
    }

    private boolean isProfileCompleted(UserProfile p) {
        // 너 서비스 정책에 맞게 조정
        return p.getEmail() != null && !p.getEmail().isBlank()
                && p.getNickname() != null && !p.getNickname().isBlank();
    }
}
