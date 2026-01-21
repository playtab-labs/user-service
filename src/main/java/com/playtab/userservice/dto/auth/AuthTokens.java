package com.playtab.userservice.dto.auth;

import lombok.Builder;

@Builder
public record AuthTokens(
        String accessToken,
        String refreshToken,
        long accessExpiresInSeconds,
        long refreshExpiresInSeconds,
        boolean profileCompleted
) {}
