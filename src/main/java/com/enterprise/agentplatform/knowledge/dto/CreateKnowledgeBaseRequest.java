package com.enterprise.agentplatform.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgeBaseRequest(
        @NotBlank(message = "name 不能为空") @Size(max = 128) String name,
        @Size(max = 255) String description
) {
}
