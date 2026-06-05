package com.enterprise.agentplatform.approval.dto;

public record ApprovalCommentTemplateResponse(
        String code,
        String action,
        String label,
        String comment
) {
}
