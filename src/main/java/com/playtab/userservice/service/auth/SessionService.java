package com.playtab.userservice.service.auth;

import com.playtab.userservice.entity.AuthIdentity;
import com.playtab.userservice.entity.AuthSession;
import com.playtab.userservice.repository.AuthSessionRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final AuthSessionRepository repo;

    public SessionService(AuthSessionRepository repo) {
        this.repo = repo;
    }

    public AuthSession create(AuthIdentity identity, String refreshTokenRaw,
                              String deviceFingerprint, String userAgent, String ipAddress,
                              Instant expiresAt) {
        AuthSession s = new AuthSession();
        s.setIdentity(identity);
        s.setRefreshTokenHash(sha256Hex(refreshTokenRaw));
        s.setDeviceFingerprint(deviceFingerprint);
        s.setUserAgent(userAgent);
        s.setIpAddress(ipAddress);
        s.setExpiresAt(expiresAt);
        s.setIsRevoked(false);
        return repo.save(s);
    }

    public Optional<AuthSession> findByRefreshToken(String refreshTokenRaw) {
        return repo.findByRefreshTokenHash(sha256Hex(refreshTokenRaw));
    }

    public void revoke(AuthSession session) {
        session.setIsRevoked(true);
        repo.save(session);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }
}
