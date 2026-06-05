package com.enterprise.agentplatform.chat.dto;

import java.util.List;

public record AskResponse(
        Long conversationId,
        String answer,
        List<CitationResponse> citations,
        String confidence,
        boolean fallback
) {
}
