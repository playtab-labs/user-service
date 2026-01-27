package com.playtab.userservice.service.auth.social;

import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SocialAuthProviderRegistry {

    private final List<SocialAuthProvider> providers;

    public SocialAuthProviderRegistry(List<SocialAuthProvider> providers) {
        this.providers = providers;
    }

    public SocialAuthProvider get(CredentialType type) {
        return providers.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER));
    }
}