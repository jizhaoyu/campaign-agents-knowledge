package com.enterprise.agentplatform.ticket.dto;

import jakarta.validation.constraints.NotNull;

public record GenerateTicketDraftRequest(
        @NotNull(message = "conversationId 不能为空") Long conversationId
) {
}
