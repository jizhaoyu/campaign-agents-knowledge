package com.enterprise.agentplatform.ai.dto;

public record AiRuntimeComponentStatus(
        boolean enabled,
        boolean modelAvailable,
        boolean credentialConfigured,
        String provider,
        String baseUrl,
        String path,
        String model
) {
}
