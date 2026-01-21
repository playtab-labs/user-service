package com.playtab.userservice.service.auth;

import com.playtab.userservice.config.JwtProperties;
import com.playtab.userservice.entity.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {

    private final JwtProperties props;
    private final SecretKey key;

    public TokenService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UUID identityId, Role role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getAccessTokenValidityInSeconds());

        return Jwts.builder()
                .issuer("user-service")
                .subject(identityId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public String issueRefreshToken(UUID identityId, UUID sessionId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getRefreshTokenValidityInSeconds());

        return Jwts.builder()
                .issuer("user-service")
                .subject(identityId.toString())
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public UUID parseAccessIdentityId(String accessToken) {
        Claims c = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();

        return UUID.fromString(c.getSubject());
    }

    public long accessTtlSeconds() {
        return props.getAccessTokenValidityInSeconds();
    }

    public long refreshTtlSeconds() {
        return props.getRefreshTokenValidityInSeconds();
    }
}
