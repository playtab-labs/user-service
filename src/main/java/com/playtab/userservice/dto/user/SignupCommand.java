package com.playtab.userservice.dto.user;

import com.playtab.userservice.entity.enums.ConsentType;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record SignupCommand(
        String email,
        String password,
        String name,
        String nickname,
        String phoneNumber,
        LocalDate birthDate,
        String nationality,
        List<Consent> consents
) {
    @Builder
    public record Consent(String termsVersion, ConsentType type, boolean isAgreed) {}
}
