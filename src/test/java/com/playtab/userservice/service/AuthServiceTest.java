package com.playtab.userservice.service;

import com.playtab.userservice.dto.auth.AuthTokens;
import com.playtab.userservice.dto.auth.LoginCommand;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import com.playtab.userservice.service.auth.AuthService;
import com.playtab.userservice.service.user.UserSignupService;
import com.playtab.userservice.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private UserSignupService signupService;

    @Autowired
    private AuthService authService;

    @Test
    void login_success_returns_tokens() {
        String email = TestDataFactory.randomEmail();
        String password = "P@ssw0rd!!";

        signupService.signUpWithEmail(
                TestDataFactory.signupCommand(email, password, TestDataFactory.randomNickname())
        );

        AuthTokens tokens = authService.loginWithEmail(LoginCommand.builder()
                .email(email)
                .password(password)
                .deviceFingerprint("dev123")
                .userAgent("JUnit")
                .ipAddress("127.0.0.1")
                .build());

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        // application-test.yml 값 기준(3600 / 1209600)
        assertThat(tokens.accessExpiresInSeconds()).isEqualTo(3600);
        assertThat(tokens.refreshExpiresInSeconds()).isEqualTo(1209600);
    }

    @Test
    void login_wrong_password_throws() {
        String email = TestDataFactory.randomEmail();

        signupService.signUpWithEmail(
                TestDataFactory.signupCommand(email, "RIGHT_PASS!!", TestDataFactory.randomNickname())
        );

        assertThatThrownBy(() ->
                authService.loginWithEmail(LoginCommand.builder()
                        .email(email)
                        .password("WRONG_PASS!!")
                        .build())
        )
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_FAILED));
    }
}
