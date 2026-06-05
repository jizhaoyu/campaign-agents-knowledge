package com.enterprise.agentplatform.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChunkingService {

    private static final int MAX_CHARS = 700;
    private static final int OVERLAP_CHARS = 120;

    public List<ChunkSlice> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<ChunkSlice> chunks = new ArrayList<>();
        int start = 0;
        int chunkNo = 1;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + MAX_CHARS);
            String content = normalized.substring(start, end).trim();
            if (!content.isBlank()) {
                chunks.add(new ChunkSlice(chunkNo++, content, start, end, estimateTokens(content)));
            }
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
        return chunks;
    }

    private int estimateTokens(String content) {
        return Math.max(1, content.split("\\s+").length);
    }

    public record ChunkSlice(int chunkNo, String content, int startOffset, int endOffset, int tokenCount) {
    }
}
