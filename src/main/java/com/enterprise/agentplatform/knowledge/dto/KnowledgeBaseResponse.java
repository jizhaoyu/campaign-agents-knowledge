package com.enterprise.agentplatform.knowledge.dto;

public record KnowledgeBaseResponse(
        Long id,
        String name,
        String description,
        String status
) {
}
