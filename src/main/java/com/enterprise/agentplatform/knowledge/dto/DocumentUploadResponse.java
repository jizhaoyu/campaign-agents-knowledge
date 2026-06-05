package com.enterprise.agentplatform.knowledge.dto;

public record DocumentUploadResponse(
        Long id,
        String fileName,
        String parseStatus,
        String indexStatus,
        long chunkCount,
        String failureReason
) {
}
