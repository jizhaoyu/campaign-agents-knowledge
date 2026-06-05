package com.enterprise.agentplatform.common.security;

import java.util.Set;

public record TokenPrincipal(Long userId, String username, Set<String> roles, Set<String> permissions) {
}
