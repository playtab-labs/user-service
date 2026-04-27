package com.playtab.userservice.service.user.passwordreset;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetState implements Serializable {
    private String codeHash;
    private Instant expiresAt;
    private int attempts;
    private boolean verified;
    private Instant lastSentAt;
}
