package com.playtab.userservice.service.user.email;

import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Random;

@Service
public class EmailVerificationService {

    private final EmailVerificationRedisRepository repo;
    private final JavaMailSender mailSender;

    @Value("${email-verification.ttl-seconds:600}")
    private long ttlSeconds;

    @Value("${email-verification.max-attempts:5}")
    private int maxAttempts;

    @Value("${email-verification.resend-cooldown-seconds:30}")
    private long resendCooldownSeconds;

    @Value("${email-verification.from:no-reply@playtab.com}")
    private String from;

    public EmailVerificationService(
            EmailVerificationRedisRepository repo,
            JavaMailSender mailSender
    ) {
        this.repo = repo;
        this.mailSender = mailSender;
    }

    /** 인증번호 발송(또는 재발송) */
    public long sendCode(String emailRaw, String sessionIdRaw) {
        String email = normalizeEmail(emailRaw);
        String sessionId = normalizeSessionId(sessionIdRaw);
        String key = redisKey(email, sessionId);

        // ✅ 쿨다운 체크 (이미 발송한 지 얼마 안 됐으면 막기)
        repo.find(key).ifPresent(existing -> {
            Instant lastSentAt = existing.getLastSentAt();
            if (lastSentAt != null) {
                Instant nextAllowed = lastSentAt.plusSeconds(resendCooldownSeconds);
                if (Instant.now().isBefore(nextAllowed)) {
                    throw new DomainException(ErrorCode.EMAIL_VERIFICATION_RESEND_TOO_FAST);
                }
            }
            // (선택) 이미 verified면 재발송을 막고 싶으면 여기서 처리
            // if (existing.isVerified()) throw new DomainException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        });

        String code = generate6DigitCode();

        EmailVerificationState state = new EmailVerificationState(
                sha256Hex(code),
                Instant.now().plusSeconds(ttlSeconds),
                0,
                false,
                Instant.now() // ✅ lastSentAt
        );

        repo.save(key, state, Duration.ofSeconds(ttlSeconds));

        sendEmail(email, code);

        return repo.ttlSeconds(key);
    }

    /** 인증번호 검증 */
    public boolean verifyCode(String emailRaw, String codeRaw, String sessionIdRaw) {
        String email = normalizeEmail(emailRaw);
        String sessionId = normalizeSessionId(sessionIdRaw);
        String key = redisKey(email, sessionId);

        EmailVerificationState state = repo.find(key)
                .orElseThrow(() -> new DomainException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));

        if (state.getExpiresAt() != null && state.getExpiresAt().isBefore(Instant.now())) {
            repo.delete(key);
            throw new DomainException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }

        if (state.getAttempts() >= maxAttempts) {
            throw new DomainException(ErrorCode.EMAIL_VERIFICATION_TOO_MANY_ATTEMPTS);
        }

        state.setAttempts(state.getAttempts() + 1);

        String inputHash = sha256Hex(codeRaw == null ? "" : codeRaw.trim());
        if (!inputHash.equals(state.getCodeHash())) {
            repo.save(key, state, Duration.ofSeconds(Math.max(repo.ttlSeconds(key), 1)));
            throw new DomainException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

        state.setVerified(true);

        // verified 상태 유지(남은 TTL 유지)
        repo.save(key, state, Duration.ofSeconds(Math.max(repo.ttlSeconds(key), 1)));
        return true;
    }

    /** 회원가입 전에 “검증완료” 강제 */
    public void assertVerifiedOrThrow(String emailRaw, String sessionIdRaw) {
        String email = normalizeEmail(emailRaw);
        String sessionId = normalizeSessionId(sessionIdRaw);
        String key = redisKey(email, sessionId);

        EmailVerificationState state = repo.find(key)
                .orElseThrow(() -> new DomainException(ErrorCode.EMAIL_NOT_VERIFIED));

        if (!state.isVerified()) throw new DomainException(ErrorCode.EMAIL_NOT_VERIFIED);

        if (state.getExpiresAt() != null && state.getExpiresAt().isBefore(Instant.now())) {
            throw new DomainException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
    }

    /** 회원가입 성공 시 재사용 방지 */
    public void consumeVerified(String emailRaw, String sessionIdRaw) {
        String email = normalizeEmail(emailRaw);
        String sessionId = normalizeSessionId(sessionIdRaw);
        repo.delete(redisKey(email, sessionId));
    }

    private void sendEmail(String to, String code) {
        System.out.println("#########################################");
        System.out.println("대상 이메일: " + to);
        System.out.println("생성된 인증번호: " + code);
        System.out.println("#########################################");

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setFrom(from);
        msg.setSubject("[PlayTab] 이메일 인증번호");
        msg.setText("인증번호: " + code + "\n유효시간: " + ttlSeconds + "초");
        mailSender.send(msg);
    }

    private String generate6DigitCode() {
        int n = 100000 + new Random().nextInt(900000);
        return String.valueOf(n);
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new DomainException(ErrorCode.INVALID_EMAIL);
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSessionId(String sid) {
        if (sid == null || sid.isBlank()) return "no_session";
        return sid.trim();
    }

    private String redisKey(String email, String sessionId) {
        return "email_verify:" + email + ":" + sessionId;
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