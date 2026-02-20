package com.playtab.userservice.dto.auth;

import com.playtab.userservice.proto.v1.ConsentInput;
import com.playtab.userservice.entity.enums.CredentialType;
import lombok.Builder;

import java.util.List;

@Builder
public record SocialLoginCommand(
        CredentialType type,
        String idToken,
        String accessToken,
        List<ConsentInput> consents,
        String deviceFingerprint,
        String userAgent,
        String ipAddress
) {}
