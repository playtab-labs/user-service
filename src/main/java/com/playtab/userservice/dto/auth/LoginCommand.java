package com.playtab.userservice.dto.auth;

import lombok.Builder;

@Builder
public record LoginCommand(
        String email,
        String password,
        String deviceFingerprint,
        String userAgent,
        String ipAddress
) {}
