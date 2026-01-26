package com.playtab.userservice.grpc.mapper;

import com.google.protobuf.Timestamp;
import com.playtab.userservice.dto.auth.AuthTokens;
import com.playtab.userservice.dto.auth.LoginCommand;
import com.playtab.userservice.proto.v1.*;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuthGrpcMapper {

    public LoginCommand toLoginCommand(LoginWithEmailRequest req) {
        ClientContext c = req.getClient();
        return LoginCommand.builder()
                .email(req.getEmail())
                .password(req.getPassword())
                .deviceFingerprint(c.getDeviceFingerprint())
                .userAgent(c.getUserAgent())
                .ipAddress(c.getIpAddress())
                .build();
    }

    public AuthTokensResponse toAuthTokensResponse(AuthTokens t) {
        return AuthTokensResponse.newBuilder()
                .setAccessToken(t.accessToken())
                .setRefreshToken(t.refreshToken())
                .setAccessExpiresInSeconds(t.accessExpiresInSeconds())
                .setRefreshExpiresInSeconds(t.refreshExpiresInSeconds())
                .setProfileCompleted(t.profileCompleted())
                .build();
    }

    public ClientMeta toClientMeta(ClientContext c) {
        return new ClientMeta(c.getDeviceFingerprint(), c.getUserAgent(), c.getIpAddress());
    }

    public MyAuthSummaryResponse toMyAuthSummaryResponse(MyAuthSummary s) {
        return MyAuthSummaryResponse.newBuilder()
                .setIdentityId(s.identityId().toString())
                .setRole(s.role())
                .setStatus(s.status())
                .setCreatedAt(toTs(s.createdAt()))
                .build();
    }

    private Timestamp toTs(Instant i) {
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }

    // refreshTokens용 간단 DTO (원하면 패키지 밖으로 빼도 됨)
    public record ClientMeta(String deviceFingerprint, String userAgent, String ipAddress) {}
    public record MyAuthSummary(java.util.UUID identityId, String role, String status, Instant createdAt) {}
}
