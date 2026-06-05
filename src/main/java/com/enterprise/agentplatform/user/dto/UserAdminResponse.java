package com.enterprise.agentplatform.user.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record UserAdminResponse(
        Long id,
        String username,
        String displayName,
        String status,
        int failedLoginCount,
        LocalDateTime lockedUntil,
        Set<String> roles
) {
}
