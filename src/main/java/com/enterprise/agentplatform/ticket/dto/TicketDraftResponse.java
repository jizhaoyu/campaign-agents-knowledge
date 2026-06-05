package com.enterprise.agentplatform.ticket.dto;

public record TicketDraftResponse(
        Long conversationId,
        String title,
        String description,
        String priority,
        Long suggestedAssigneeId
) {
}
