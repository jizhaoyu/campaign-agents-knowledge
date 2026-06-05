package com.enterprise.agentplatform.approval.dto;

import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
        @Size(max = 64, message = "templateCode 长度不能超过 64") String templateCode,
        @Size(max = 255, message = "comment 长度不能超过 255") String comment
) {
}
