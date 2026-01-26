package com.playtab.userservice.service;

import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.repository.AuthCredentialRepository;
import com.playtab.userservice.repository.UserProfileRepository;
import com.playtab.userservice.service.user.UserSignupService;
import com.playtab.userservice.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.playtab.userservice.entity.enums.CredentialType.EMAIL;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserSignupServiceTest {

    @Autowired
    private UserSignupService signupService;

    @Autowired
    private AuthCredentialRepository credentialRepo;

    @Autowired
    private UserProfileRepository profileRepo;

    @Test
    void signUp_success_creates_identity_credential_profile() {
        String email = TestDataFactory.randomEmail();
        String nick = TestDataFactory.randomNickname();

        SignupCommand cmd = TestDataFactory.signupCommand(email, "P@ssw0rd!!", nick);

        UUID identityId = signupService.signUpWithEmail(cmd);

        assertThat(identityId).isNotNull();
        assertThat(credentialRepo.existsByTypeAndIdentifier(EMAIL, email)).isTrue();
        assertThat(profileRepo.findByIdentity_IdentityId(identityId)).isPresent();
    }

    @Test
    void signUp_duplicate_email_throws() {
        String email = TestDataFactory.randomEmail();

        signupService.signUpWithEmail(
                TestDataFactory.signupCommand(email, "P@ssw0rd!!", TestDataFactory.randomNickname())
        );

        assertThatThrownBy(() ->
                signupService.signUpWithEmail(
                        TestDataFactory.signupCommand(email, "P@ssw0rd!!", TestDataFactory.randomNickname())
                )
        )
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }
}
