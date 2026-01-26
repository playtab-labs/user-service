package com.playtab.userservice.dto.auth;

import lombok.Builder;

@Builder
public record LogoutCommand(String refreshToken) {}
