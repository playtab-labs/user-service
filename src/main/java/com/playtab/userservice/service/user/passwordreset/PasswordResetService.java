package com.playtab.userservice.service.user.passwordreset;

import com.playtab.userservice.entity.AuthCredential;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.entity.enums.IdentityStatus;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthCredentialRepository;
import com.playtab.userservice.service.auth.PasswordService;
import com.playtab.userservice.service.mail.MailTemplateService;
import com.playtab.userservice.service.mail.MailTemplateType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@Service
public class PasswordResetService {

    private final PasswordResetRedisRepository repo;
    private final MailTemplateService mailTemplateService;
    private final AuthCredentialRepository credentialRepo;
    private final PasswordService passwordService;

    @Value("${password-reset.ttl-seconds:600}")
    private long ttlSeconds;

    @Value("${password-reset.max-attempts:5}")
    private int maxAttempts;

    @Value("${password-reset.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    public PasswordResetService(
            PasswordResetRedisRepository repo,
            MailTemplateService mailTemplateService,
            AuthCredentialRepository credentialRepo,
            PasswordService passwordService
    ) {
        this.repo = repo;
        this.mailTemplateService = mailTemplateService;
        this.credentialRepo = credentialRepo;
        this.passwordService = passwordService;
    }

    /** 비밀번호 재설정 코드 발송 */
    public long sendCode(String emailRaw) {
        String email = normalizeEmail(emailRaw);
        String key = redisKey(email);

        if (!credentialRepo.existsByTypeAndIdentifier(CredentialType.EMAIL, email)) {
            throw new DomainException(ErrorCode.EMAIL_NOT_REGISTERED);
        }

        repo.find(key).ifPresent(existing -> {
            Instant lastSentAt = existing.getLastSentAt();
            if (lastSentAt != null) {
                Instant nextAllowed = lastSentAt.plusSeconds(resendCooldownSeconds);
                if (Instant.now().isBefore(nextAllowed)) {
                    throw new DomainException(ErrorCode.PASSWORD_RESET_RESEND_TOO_FAST);
                }
            }
        });

        String code = generate6DigitCode();

        PasswordResetState state = new PasswordResetState(
                sha256Hex(code),
                Instant.now().plusSeconds(ttlSeconds),
                0,
                false,
                Instant.now()
        );

        repo.save(key, state, Duration.ofSeconds(ttlSeconds));

        mailTemplateService.sendHtmlMail(
                email,
                "[PlayTap] 비밀번호 재설정 인증번호",
                MailTemplateType.PASSWORD_RESET.templateName(),
                Map.of("code", code, "ttlSeconds", ttlSeconds)
        );

        return repo.ttlSeconds(key);
    }

    /** 인증번호 검증 */
    public void verifyCode(String emailRaw, String codeRaw) {
        String email = normalizeEmail(emailRaw);
        String key = redisKey(email);

        PasswordResetState state = repo.find(key)
                .orElseThrow(() -> new DomainException(ErrorCode.PASSWORD_RESET_NOT_FOUND));

        if (state.getExpiresAt() != null && state.getExpiresAt().isBefore(Instant.now())) {
            repo.delete(key);
            throw new DomainException(ErrorCode.PASSWORD_RESET_EXPIRED);
        }

        if (state.getAttempts() >= maxAttempts) {
            throw new DomainException(ErrorCode.PASSWORD_RESET_TOO_MANY_ATTEMPTS);
        }

        state.setAttempts(state.getAttempts() + 1);

        String inputHash = sha256Hex(codeRaw == null ? "" : codeRaw.trim());
        if (!inputHash.equals(state.getCodeHash())) {
            repo.save(key, state, Duration.ofSeconds(Math.max(repo.ttlSeconds(key), 1)));
            throw new DomainException(ErrorCode.PASSWORD_RESET_CODE_MISMATCH);
        }

        state.setVerified(true);
        repo.save(key, state, Duration.ofSeconds(Math.max(repo.ttlSeconds(key), 1)));
    }

    /** 새 비밀번호로 재설정 */
    @Transactional
    public void resetPassword(String emailRaw, String newPassword) {
        String email = normalizeEmail(emailRaw);
        String key = redisKey(email);

        if (newPassword == null || newPassword.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_REQUEST);
        }

        PasswordResetState state = repo.find(key)
                .orElseThrow(() -> new DomainException(ErrorCode.PASSWORD_RESET_NOT_FOUND));

        if (!state.isVerified()) {
            throw new DomainException(ErrorCode.PASSWORD_RESET_NOT_VERIFIED);
        }

        if (state.getExpiresAt() != null && state.getExpiresAt().isBefore(Instant.now())) {
            repo.delete(key);
            throw new DomainException(ErrorCode.PASSWORD_RESET_EXPIRED);
        }

        AuthCredential cred = credentialRepo.findByTypeAndIdentifier(CredentialType.EMAIL, email)
                .orElseThrow(() -> new DomainException(ErrorCode.EMAIL_CREDENTIAL_NOT_FOUND));

        if (cred.getIdentity().getStatus() == IdentityStatus.DELETED) {
            throw new DomainException(ErrorCode.ACCOUNT_DELETED);
        }
        if (cred.getIdentity().getStatus() == IdentityStatus.LOCKED) {
            throw new DomainException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (passwordService.matches(newPassword, cred.getPasswordHash())) {
            throw new DomainException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }

        cred.setPasswordHash(passwordService.hash(newPassword));
        credentialRepo.saveAndFlush(cred);

        repo.delete(key);
    }

    private String redisKey(String email) {
        return "user:password_reset:" + email;
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new DomainException(ErrorCode.INVALID_EMAIL);
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new DomainException(ErrorCode.INVALID_EMAIL);
        return normalized;
    }

    private String generate6DigitCode() {
        int n = 100000 + new Random().nextInt(900000);
        return String.valueOf(n);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
