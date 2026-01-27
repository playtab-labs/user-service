package com.playtab.userservice.service.auth.social;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.playtab.userservice.dto.auth.SocialLoginCommand;
import com.playtab.userservice.entity.enums.CredentialType;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class GoogleAuthProvider implements SocialAuthProvider {

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthProvider(@Value("${oauth.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @Override
    public boolean supports(CredentialType type) {
        return type == CredentialType.GOOGLE;
    }

    @Override
    public SocialUserInfo verifyAndGetUserInfo(SocialLoginCommand cmd) {
        if (cmd.idToken() == null || cmd.idToken().isBlank()) {
            throw new DomainException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }

        try {
            GoogleIdToken idToken = verifier.verify(cmd.idToken());
            if (idToken == null) throw new DomainException(ErrorCode.INVALID_SOCIAL_TOKEN);

            GoogleIdToken.Payload p = idToken.getPayload();

            String sub = p.getSubject();
            String email = p.getEmail();
            Boolean emailVerified = p.getEmailVerified();

            return SocialUserInfo.builder()
                    .type(CredentialType.GOOGLE)
                    .providerUserId(sub)
                    .email(email)
                    .emailVerified(emailVerified)
                    .build();

        } catch (Exception e) {
            throw new DomainException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }
}