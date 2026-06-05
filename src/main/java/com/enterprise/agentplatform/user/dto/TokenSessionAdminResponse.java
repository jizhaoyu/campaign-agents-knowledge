package com.enterprise.agentplatform.user.dto;

import java.time.LocalDateTime;

public record TokenSessionAdminResponse(
        Long id,
        Long userId,
        String username,
        String tokenFingerprint,
        String roleCodes,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        LocalDateTime refreshExpiresAt,
        LocalDateTime lastRefreshedAt,
        LocalDateTime revokedAt,
        boolean accessTokenActive,
        boolean active
) {
}
