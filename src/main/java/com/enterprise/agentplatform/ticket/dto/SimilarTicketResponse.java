package com.enterprise.agentplatform.ticket.dto;

import java.util.List;

public record SimilarTicketResponse(
        Long ticketId,
        String title,
        String priority,
        String status,
        int score,
        List<String> matchedKeywords,
        String matchSummary
) {
}
