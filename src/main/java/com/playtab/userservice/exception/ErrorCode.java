package com.playtab.userservice.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    AUTH_FAILED("AUTH_FAILED", "Invalid email or password."),
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "Email already exists."),
    DUPLICATE_NICKNAME("DUPLICATE_NICKNAME", "Nickname already exists."),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "Account is locked."),
    ACCOUNT_DELETED("ACCOUNT_DELETED", "Account is deleted.");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
