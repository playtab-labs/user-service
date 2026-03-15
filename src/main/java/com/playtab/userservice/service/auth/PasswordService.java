package com.playtab.userservice.service.auth;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final Argon2 argon2 =
            Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    public String hash(String rawPassword) {
        return argon2.hash(3, 65536, 1, rawPassword.toCharArray());
    }

    public boolean matches(String rawPassword, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) return false;
        return argon2.verify(passwordHash, rawPassword.toCharArray());
    }
}

