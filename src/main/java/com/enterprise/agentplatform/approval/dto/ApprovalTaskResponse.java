package com.enterprise.agentplatform.approval.dto;

public record ApprovalTaskResponse(
        Long id,
        String targetType,
        Long targetId,
        Long approverId,
        String status,
        String comment
) {
}
