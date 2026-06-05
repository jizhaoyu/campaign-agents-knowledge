package com.enterprise.agentplatform.search.service;

import com.enterprise.agentplatform.chat.dto.CitationResponse;

public record RetrievalResult(
        CitationResponse citation,
        double score
) {
}
