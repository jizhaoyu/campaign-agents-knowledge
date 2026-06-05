package com.enterprise.agentplatform.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiRuntimeStatusResponse(
        List<String> activeProfiles,
        AiRuntimeComponentStatus chat,
        AiRuntimeComponentStatus embedding,
        String readinessLevel,
        List<String> warnings,
        LocalDateTime generatedAt
) {
}
