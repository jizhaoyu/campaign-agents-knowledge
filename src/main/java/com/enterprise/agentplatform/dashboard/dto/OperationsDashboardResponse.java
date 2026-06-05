package com.enterprise.agentplatform.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsDashboardResponse(
        long knowledgeBaseCount,
        long documentCount,
        long pendingIndexTaskCount,
        long runningIndexTaskCount,
        long failedIndexTaskCount,
        long failedDocumentCount,
        long pendingApprovalCount,
        long activeHighRiskTicketCount,
        long pendingHighRiskTicketCount,
        long activeTokenSessionCount,
        String healthLevel,
        long alertCount,
        String healthSummary,
        List<String> recommendedActions,
        LocalDateTime generatedAt
) {
}
