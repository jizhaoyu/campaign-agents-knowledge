package com.enterprise.agentplatform.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatHistoryItemResponse(
        Long conversationId,
        Long knowledgeBaseId,
        String question,
        String answer,
        List<CitationResponse> citations,
        String confidence,
        boolean fallback,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
