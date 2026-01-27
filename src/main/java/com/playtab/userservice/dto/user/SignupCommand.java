package com.playtab.userservice.dto.user;

import com.playtab.userservice.entity.enums.ConsentType;
import com.playtab.userservice.entity.enums.Gender;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record SignupCommand(
        String email,
        String password,
        String name,
        Gender gender,
        String phoneNumber,
        LocalDate birthDate,
        String nationality,
        List<Consent> consents,
        String sessionId
) {
    @Builder
    public record Consent(String termsVersion, ConsentType type, boolean isAgreed) {}
}