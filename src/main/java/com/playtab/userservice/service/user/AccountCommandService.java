package com.playtab.userservice.service.user;

import com.playtab.userservice.entity.AuthCredential;
import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.AuthSession;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.entity.enums.IdentityStatus;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthCredentialRepository;
import com.playtab.userservice.repository.AuthIdentityRepository;
import com.playtab.userservice.repository.AuthSessionRepository;
import com.playtab.userservice.service.auth.PasswordService;
import com.playtab.userservice.service.auth.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountCommandService {

    private final AuthIdentityRepository identityRepo;
    private final AuthCredentialRepository credentialRepo;
    private final AuthSessionRepository sessionRepo;
    private final SessionService sessionService;
    private final PasswordService passwordService;

    /**
     * 비밀번호 변경
     * - 로그인한 사용자만 가능
     * - EMAIL credential 이 있어야 함
     * - 현재 비밀번호 검증 후 새 비밀번호로 변경
     */
    @Transactional
    public void changeMyPassword(UUID identityId, String currentPassword, String newPassword) {
        if (identityId == null) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED);
        }

        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_REQUEST);
        }

        AuthIdentity identity = identityRepo.findById(identityId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));

        validateActiveIdentity(identity);

        AuthCredential emailCred = credentialRepo
                .findByIdentity_IdentityIdAndType(identityId, CredentialType.EMAIL)
                .orElseThrow(() -> new DomainException(ErrorCode.EMAIL_CREDENTIAL_NOT_FOUND));

        if (!passwordService.matches(currentPassword, emailCred.getPasswordHash())) {
            throw new DomainException(ErrorCode.AUTH_FAILED);
        }

        if (passwordService.matches(newPassword, emailCred.getPasswordHash())) {
            throw new DomainException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }

        emailCred.setPasswordHash(passwordService.hash(newPassword));
        credentialRepo.save(emailCred);

        // 선택 정책:
        // 비밀번호 변경 후 기존 세션 전체 만료시키고 재로그인 유도
        sessionRepo.revokeAllByIdentityId(identityId);
    }

    /**
     * 회원탈퇴
     * - identity.status = DELETED
     * - 현재 refresh token 세션 유효성 확인
     * - 전체 세션 revoke
     */
    @Transactional
    public void withdrawMyAccount(UUID identityId, String refreshToken) {
        if (identityId == null) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED);
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        AuthIdentity identity = identityRepo.findById(identityId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));

        validateActiveIdentity(identity);

        AuthSession session = sessionService.findByRefreshTokenForUpdate(refreshToken)
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN));

        sessionService.validateActiveSessionOrThrow(session);

        if (!identityId.equals(session.getIdentity().getIdentityId())) {
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        identity.setStatus(IdentityStatus.DELETED);
        identityRepo.saveAndFlush(identity);

        sessionRepo.revokeAllByIdentityId(identityId);
    }

    private void validateActiveIdentity(AuthIdentity identity) {
        if (identity.getStatus() == IdentityStatus.DELETED) {
            throw new DomainException(ErrorCode.ACCOUNT_DELETED);
        }
        if (identity.getStatus() == IdentityStatus.LOCKED) {
            throw new DomainException(ErrorCode.ACCOUNT_LOCKED);
        }
    }
}