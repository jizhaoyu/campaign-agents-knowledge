package com.enterprise.agentplatform.audit.dto;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String eventType,
        String targetType,
        Long targetId,
        String traceId,
        String payloadJson
) {
}
