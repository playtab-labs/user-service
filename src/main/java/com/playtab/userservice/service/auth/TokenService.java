package com.playtab.userservice.service.auth;

import com.playtab.userservice.config.JwtProperties;
import com.playtab.userservice.entity.enums.Role;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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

    public UUID parseSubject(String jwt) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(jwt).getPayload();
            return UUID.fromString(c.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    public UUID parseSessionId(String refreshJwt) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(refreshJwt).getPayload();
            Object sid = c.get("sid");
            if (sid == null) throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
            return UUID.fromString(sid.toString());
        } catch (JwtException | IllegalArgumentException e) {
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    public long accessTtlSeconds() {
        return props.getAccessTokenValidityInSeconds();
    }

    public long refreshTtlSeconds() {
        return props.getRefreshTokenValidityInSeconds();
    }
}
