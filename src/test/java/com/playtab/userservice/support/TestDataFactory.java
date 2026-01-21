package com.playtab.userservice.support;

import com.playtab.userservice.dto.user.SignupCommand;
import com.playtab.userservice.entity.enums.ConsentType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class TestDataFactory {

    public static SignupCommand signupCommand(String email, String password, String nickname) {
        return SignupCommand.builder()
                .email(email)
                .password(password)
                .name("테스터")
                .nickname(nickname)
                .phoneNumber("+821012345678")
                .birthDate(LocalDate.of(2000, 1, 1))
                .nationality("KR")
                .consents(List.of(
                        SignupCommand.Consent.builder().termsVersion("v1.0").type(ConsentType.SERVICE).isAgreed(true).build(),
                        SignupCommand.Consent.builder().termsVersion("v1.0").type(ConsentType.PRIVACY).isAgreed(true).build()
                ))
                .build();
    }

    public static String randomEmail() {
        return "test+" + UUID.randomUUID() + "@playtab.com";
    }

    public static String randomNickname() {
        return "nick_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
