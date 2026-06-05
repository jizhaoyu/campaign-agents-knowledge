package com.enterprise.agentplatform.chat.dto;

public record CitationResponse(
        Long documentId,
        Long chunkId,
        String documentName,
        String snippet
) {
}
