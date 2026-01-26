package com.playtab.userservice.dto.user;

import java.time.LocalDate;

public record ProfilePatch(
        String name,
        String nickname,
        String phoneNumber,
        LocalDate birthDate,
        String nationality
) {}
