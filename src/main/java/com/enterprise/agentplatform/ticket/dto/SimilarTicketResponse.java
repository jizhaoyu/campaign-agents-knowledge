package com.enterprise.agentplatform.ticket.dto;

public record SimilarTicketResponse(
        Long ticketId,
        String title,
        String priority,
        String status,
        int score
) {
}
