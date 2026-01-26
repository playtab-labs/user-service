package com.playtab.userservice.service.user.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationState implements Serializable {
    private String codeHash;     // sha256(code)
    private Instant expiresAt;
    private int attempts;        // 검증 시도 횟수
    private boolean verified;    // 검증 완료 여부
    private Instant lastSentAt;  // 마지막 발송 시각
}