package com.playtab.userservice.dto.user;

import com.playtab.userservice.entity.enums.Gender;
import java.time.LocalDate;

public record ProfilePatch(
        String name,
        String phoneNumber,
        LocalDate birthDate,
        String nationality,
        Gender gender
) {}