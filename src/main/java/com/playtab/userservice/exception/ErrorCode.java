package com.playtab.userservice.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    AUTH_FAILED("AUTH_FAILED", "Invalid email or password."),
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "Email already exists."),
    DUPLICATE_NICKNAME("DUPLICATE_NICKNAME", "Nickname already exists."),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "Account is locked."),
    ACCOUNT_DELETED("ACCOUNT_DELETED", "Account is deleted."),

    INVALID_REQUEST("INVALID_REQUEST", "Invalid request."),
    UNAUTHENTICATED("UNAUTHENTICATED", "Authentication required."),
    FORBIDDEN("FORBIDDEN", "No permission."),
    NOT_FOUND("NOT_FOUND", "Resource not found."),

    REFRESH_TOKEN_INVALID("REFRESH_TOKEN_INVALID", "Invalid refresh token."),
    REFRESH_TOKEN_EXPIRED("REFRESH_TOKEN_EXPIRED", "Refresh token expired."),
    SESSION_REVOKED("SESSION_REVOKED", "Session revoked.");


    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
