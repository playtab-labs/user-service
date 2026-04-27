package com.playtab.userservice.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // ---------- Auth (Login/Token) ----------
    AUTH_FAILED("AUTH_FAILED", "Invalid email or password."),
    UNAUTHENTICATED("UNAUTHENTICATED", "Authentication required."),
    FORBIDDEN("FORBIDDEN", "No permission."),

    // Social login
    INVALID_SOCIAL_TOKEN("INVALID_SOCIAL_TOKEN", "Invalid social token."),
    UNSUPPORTED_SOCIAL_PROVIDER("UNSUPPORTED_SOCIAL_PROVIDER", "Unsupported social provider."),
    SOCIAL_EMAIL_REQUIRED("SOCIAL_EMAIL_REQUIRED", "Email is required for social login."),

    // Refresh/session
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "Invalid refresh token."),
    REFRESH_TOKEN_EXPIRED("REFRESH_TOKEN_EXPIRED", "Refresh token expired."),
    REFRESH_TOKEN_REVOKED("REFRESH_TOKEN_REVOKED", "Refresh token is revoked."),

    // Account status
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "Account is locked."),
    ACCOUNT_DELETED("ACCOUNT_DELETED", "Account is deleted."),

    // ---------- Signup/Profile ----------
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "Email already exists."),
    DUPLICATE_NICKNAME("DUPLICATE_NICKNAME", "Nickname already exists."),
    PROFILE_NOT_FOUND("PROFILE_NOT_FOUND", "Profile not found."),
    EMAIL_CREDENTIAL_NOT_FOUND("EMAIL_CREDENTIAL_NOT_FOUND", "Email credential not found."),
    SAME_AS_OLD_PASSWORD("SAME_AS_OLD_PASSWORD", "New password must be different from current password."),

    // Consent / Terms
    REQUIRED_CONSENT_MISSING("REQUIRED_CONSENT_MISSING", "Required consents (SERVICE/PRIVACY) must be agreed."),
    TERMS_VERSION_REQUIRED("TERMS_VERSION_REQUIRED", "terms_version is required."),
    CONSENT_TYPE_UNSPECIFIED("CONSENT_TYPE_UNSPECIFIED", "consent type is unspecified."),

    // ---------- Email Verification ----------
    INVALID_EMAIL("INVALID_EMAIL", "Invalid email."),
    EMAIL_VERIFICATION_NOT_FOUND("EMAIL_VERIFICATION_NOT_FOUND", "Verification code not found."),
    EMAIL_VERIFICATION_EXPIRED("EMAIL_VERIFICATION_EXPIRED", "Verification code expired."),
    EMAIL_VERIFICATION_CODE_MISMATCH("EMAIL_VERIFICATION_CODE_MISMATCH", "Verification code mismatch."),
    EMAIL_VERIFICATION_TOO_MANY_ATTEMPTS("EMAIL_VERIFICATION_TOO_MANY_ATTEMPTS", "Too many attempts."),
    EMAIL_NOT_VERIFIED("EMAIL_NOT_VERIFIED", "Email not verified."),
    EMAIL_VERIFICATION_RESEND_TOO_FAST("EMAIL_VERIFICATION_RESEND_TOO_FAST", "Please wait before resending the code."),
    EMAIL_NOT_REGISTERED("EMAIL_NOT_REGISTERED", "Email is not registered."),

    // ---------- Password Reset ----------
    PASSWORD_RESET_NOT_FOUND("PASSWORD_RESET_NOT_FOUND", "Password reset code not found."),
    PASSWORD_RESET_EXPIRED("PASSWORD_RESET_EXPIRED", "Password reset code expired."),
    PASSWORD_RESET_CODE_MISMATCH("PASSWORD_RESET_CODE_MISMATCH", "Password reset code mismatch."),
    PASSWORD_RESET_TOO_MANY_ATTEMPTS("PASSWORD_RESET_TOO_MANY_ATTEMPTS", "Too many password reset attempts."),
    PASSWORD_RESET_NOT_VERIFIED("PASSWORD_RESET_NOT_VERIFIED", "Password reset code not verified."),
    PASSWORD_RESET_RESEND_TOO_FAST("PASSWORD_RESET_RESEND_TOO_FAST", "Please wait before resending the reset code."),

    // ---------- Common ----------
    INVALID_REQUEST("INVALID_REQUEST", "Invalid request."),
    NOT_FOUND("NOT_FOUND", "Resource not found."),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error.");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}