package com.playtab.userservice.service.auth.social;

import com.playtab.userservice.entity.enums.CredentialType;
import lombok.Builder;

@Builder
public record SocialUserInfo(
        CredentialType type,
        String providerUserId,   // sub / naver id / kakao id
        String email,
        Boolean emailVerified
) {}