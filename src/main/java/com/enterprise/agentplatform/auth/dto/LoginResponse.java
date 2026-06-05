package com.enterprise.agentplatform.auth.dto;

import java.util.Set;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        String username,
        String displayName,
        Set<String> roles,
        Set<String> permissions
) {
}
