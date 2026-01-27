package com.playtab.userservice.service.auth.social;

import com.playtab.userservice.dto.auth.SocialLoginCommand;

public interface SocialAuthProvider {
    boolean supports(com.playtab.userservice.entity.enums.CredentialType type);
    SocialUserInfo verifyAndGetUserInfo(SocialLoginCommand cmd);
}