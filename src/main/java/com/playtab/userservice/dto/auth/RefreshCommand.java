package com.playtab.userservice.dto.auth;

import lombok.Builder;

@Builder
public record RefreshCommand(
        String refreshToken,
        String deviceFingerprint,
        String userAgent,
        String ipAddress
) {}
