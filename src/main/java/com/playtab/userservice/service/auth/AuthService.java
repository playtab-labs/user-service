package com.playtab.userservice.service.auth;

import com.playtab.userservice.dto.auth.AuthTokens;
import com.playtab.userservice.dto.auth.LoginCommand;
import com.playtab.userservice.entity.AuthCredential;
import com.playtab.userservice.entity.AuthIdentity;
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

    private boolean isProfileCompleted(UserProfile p) {
        // 너 서비스 정책에 맞게 조정
        return p.getEmail() != null && !p.getEmail().isBlank()
                && p.getNickname() != null && !p.getNickname().isBlank();
    }
}
