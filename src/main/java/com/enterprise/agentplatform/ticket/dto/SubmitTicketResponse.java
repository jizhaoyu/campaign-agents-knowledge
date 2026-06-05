package com.enterprise.agentplatform.ticket.dto;

public record SubmitTicketResponse(
        Long ticketId,
        String status,
        boolean approvalRequired,
        Long approvalTaskId
) {
}
