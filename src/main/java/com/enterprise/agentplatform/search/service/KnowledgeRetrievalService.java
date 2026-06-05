package com.enterprise.agentplatform.search.service;

import com.enterprise.agentplatform.ai.service.EmbeddingService;
import com.enterprise.agentplatform.chat.dto.CitationResponse;
import com.enterprise.agentplatform.domain.entity.DocumentChunk;
import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.repository.DocumentChunkRepository;
import com.enterprise.agentplatform.domain.repository.DocumentRecordRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRetrievalService {

    private static final List<String> STOPWORDS = List.of("的", "了", "吗", "如何", "怎么", "请问", "what", "how");

    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRecordRepository documentRecordRepository;
    private final EmbeddingService embeddingService;

    public KnowledgeRetrievalService(
            DocumentChunkRepository documentChunkRepository,
            DocumentRecordRepository documentRecordRepository,
            EmbeddingService embeddingService
    ) {
        this.documentChunkRepository = documentChunkRepository;
        this.documentRecordRepository = documentRecordRepository;
        this.embeddingService = embeddingService;
    }

    public List<CitationResponse> retrieve(Long knowledgeBaseId, String question) {
        return retrieveWithScores(knowledgeBaseId, question)
                .stream()
                .map(RetrievalResult::citation)
                .toList();
    }

    public List<RetrievalResult> retrieveWithScores(Long knowledgeBaseId, String question) {
        Set<String> keywords = extractKeywords(question);
        Optional<List<Double>> queryEmbedding = embeddingService.embed(question);
        List<ScoredChunk> scoredChunks = documentChunkRepository.findByKnowledgeBaseId(knowledgeBaseId)
                .stream()
                .map(chunk -> score(chunk, keywords, queryEmbedding))
                .filter(scored -> scored.score() > 0.0d)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(3)
                .toList();
        return buildResults(scoredChunks);
    }

    private ScoredChunk score(DocumentChunk chunk, Set<String> keywords, Optional<List<Double>> queryEmbedding) {
        double keywordScore = keywordScore(chunk.getContent(), keywords);
        double embeddingScore = queryEmbedding
                .flatMap(query -> embeddingService.fromJson(chunk.getEmbeddingJson())
                        .map(chunkEmbedding -> embeddingService.cosineSimilarity(query, chunkEmbedding)))
                .orElse(0.0d);
        return new ScoredChunk(chunk, keywordScore + (embeddingScore * 4.0d));
    }

    private int keywordScore(String content, Set<String> keywords) {
        String lower = content.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                score += 2;
            }
        }
        return score;
    }

    private Set<String> extractKeywords(String question) {
        String normalized = question.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsAlphabetic}\\p{IsIdeographic}\\p{IsDigit}]+", " ");
        return java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .filter(token -> token.length() > 1)
                .filter(token -> !STOPWORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private List<RetrievalResult> buildResults(List<ScoredChunk> scoredChunks) {
        List<Long> documentIds = scoredChunks.stream().map(scored -> scored.chunk().getDocumentId()).distinct().toList();
        Map<Long, DocumentRecord> documentMap = new HashMap<>();
        documentRecordRepository.findAllById(documentIds).forEach(document -> documentMap.put(document.getId(), document));
        List<RetrievalResult> results = new ArrayList<>();
        for (ScoredChunk scoredChunk : scoredChunks) {
            DocumentRecord document = documentMap.get(scoredChunk.chunk().getDocumentId());
            results.add(new RetrievalResult(
                    new CitationResponse(
                            scoredChunk.chunk().getDocumentId(),
                            scoredChunk.chunk().getId(),
                            document == null ? "unknown" : document.getFileName(),
                            trimSnippet(scoredChunk.chunk().getContent())
                    ),
                    scoredChunk.score()
            ));
        }
        return results;
    }

    private String trimSnippet(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {
    }
}
