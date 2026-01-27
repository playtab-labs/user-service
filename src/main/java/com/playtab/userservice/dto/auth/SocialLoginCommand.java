package com.playtab.userservice.dto.auth;

import com.playtab.userservice.entity.enums.CredentialType;
import lombok.Builder;

@Builder
public record SocialLoginCommand(
        CredentialType type,     // GOOGLE/NAVER/KAKAO
        String idToken,          // OIDC면 권장(구글)
        String accessToken,      // 없으면 null
        String deviceFingerprint,
        String userAgent,
        String ipAddress
) {}