package com.enterprise.agentplatform.chat.service;

import com.enterprise.agentplatform.ai.service.AnswerGenerationService;
import com.enterprise.agentplatform.audit.service.AuditService;
import com.enterprise.agentplatform.chat.dto.AskRequest;
import com.enterprise.agentplatform.chat.dto.AskResponse;
import com.enterprise.agentplatform.chat.dto.ChatHistoryItemResponse;
import com.enterprise.agentplatform.chat.dto.CitationResponse;
import com.enterprise.agentplatform.common.security.CurrentUserService;
import com.enterprise.agentplatform.domain.entity.Conversation;
import com.enterprise.agentplatform.domain.entity.MessageRecord;
import com.enterprise.agentplatform.domain.enums.ConversationStatus;
import com.enterprise.agentplatform.domain.enums.MessageRole;
import com.enterprise.agentplatform.domain.repository.ConversationRepository;
import com.enterprise.agentplatform.domain.repository.MessageRecordRepository;
import com.enterprise.agentplatform.knowledge.service.KnowledgeBaseService;
import com.enterprise.agentplatform.search.service.KnowledgeRetrievalService;
import com.enterprise.agentplatform.search.service.RetrievalResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final ConversationRepository conversationRepository;
    private final MessageRecordRepository messageRecordRepository;
    private final CurrentUserService currentUserService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final AnswerGenerationService answerGenerationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final int minCitationCount;
    private final double minTopScore;
    private static final TypeReference<List<CitationResponse>> CITATION_LIST_TYPE = new TypeReference<>() {
    };

    public ChatService(
            KnowledgeBaseService knowledgeBaseService,
            ConversationRepository conversationRepository,
            MessageRecordRepository messageRecordRepository,
            CurrentUserService currentUserService,
            KnowledgeRetrievalService knowledgeRetrievalService,
            AnswerGenerationService answerGenerationService,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${app.rag.min-citation-count:1}") int minCitationCount,
            @Value("${app.rag.min-top-score:1.0}") double minTopScore
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.conversationRepository = conversationRepository;
        this.messageRecordRepository = messageRecordRepository;
        this.currentUserService = currentUserService;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.answerGenerationService = answerGenerationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.minCitationCount = Math.max(1, minCitationCount);
        this.minTopScore = Math.max(0.0d, minTopScore);
    }

    @Transactional
    public AskResponse ask(AskRequest request) {
        knowledgeBaseService.requireExisting(request.knowledgeBaseId());
        Conversation conversation = new Conversation();
        conversation.setKnowledgeBaseId(request.knowledgeBaseId());
        conversation.setUserId(currentUserService.requireUserId());
        conversation.setStatus(ConversationStatus.OPEN);
        conversation = conversationRepository.save(conversation);

        MessageRecord userMessage = new MessageRecord();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(request.question());
        messageRecordRepository.save(userMessage);
        auditService.record("QUESTION_ASKED", "CONVERSATION", conversation.getId(), Map.of("question", request.question()));

        List<RetrievalResult> retrievalResults = knowledgeRetrievalService.retrieveWithScores(request.knowledgeBaseId(), request.question());
        double topScore = retrievalResults.isEmpty() ? 0.0d : retrievalResults.get(0).score();
        String refusalReason = refusalReason(retrievalResults, topScore);
        boolean fallback = refusalReason != null;
        List<CitationResponse> citations = fallback
                ? List.of()
                : retrievalResults.stream().map(RetrievalResult::citation).toList();
        String answer = answerGenerationService.generateAnswer(request.question(), citations);

        MessageRecord assistantMessage = new MessageRecord();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setContent(answer);
        assistantMessage.setCitationJson(toJson(citations));
        messageRecordRepository.save(assistantMessage);

        if (!citations.isEmpty()) {
            auditService.record("CITATION_SELECTED", "CONVERSATION", conversation.getId(), citations);
        }
        auditService.record("ANSWER_RETURNED", "CONVERSATION", conversation.getId(), Map.of(
                "fallback", fallback,
                "citationCount", citations.size(),
                "retrievedCitationCount", retrievalResults.size(),
                "topScore", topScore,
                "refusalReason", refusalReason == null ? "NONE" : refusalReason
        ));

        return new AskResponse(conversation.getId(), answer, citations, confidenceOf(citations.size()), fallback);
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryItemResponse> history(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Long userId = currentUserService.requireUserId();
        return conversationRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toHistoryItem)
                .toList();
    }

    private ChatHistoryItemResponse toHistoryItem(Conversation conversation) {
        String question = latestMessage(conversation.getId(), MessageRole.USER)
                .map(MessageRecord::getContent)
                .orElse("");
        MessageRecord assistantMessage = latestMessage(conversation.getId(), MessageRole.ASSISTANT)
                .orElse(null);
        String answer = assistantMessage == null ? "" : assistantMessage.getContent();
        List<CitationResponse> citations = assistantMessage == null
                ? List.of()
                : citationsFromJson(assistantMessage.getCitationJson());
        return new ChatHistoryItemResponse(
                conversation.getId(),
                conversation.getKnowledgeBaseId(),
                question,
                answer,
                citations,
                confidenceOf(citations.size()),
                citations.isEmpty(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private String refusalReason(List<RetrievalResult> retrievalResults, double topScore) {
        if (retrievalResults.isEmpty()) {
            return "NO_EVIDENCE";
        }
        if (retrievalResults.size() < minCitationCount) {
            return "INSUFFICIENT_CITATIONS";
        }
        if (topScore < minTopScore) {
            return "LOW_RELEVANCE";
        }
        return null;
    }

    private String confidenceOf(int citationCount) {
        if (citationCount == 0) {
            return "NONE";
        }
        if (citationCount >= 3) {
            return "HIGH";
        }
        if (citationCount == 2) {
            return "MEDIUM";
        }
        return citationCount == 1 ? "LOW" : "LOW";
    }

    private String toJson(List<CitationResponse> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<CitationResponse> citationsFromJson(String citationJson) {
        if (citationJson == null || citationJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citationJson, CITATION_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private java.util.Optional<MessageRecord> latestMessage(Long conversationId, MessageRole role) {
        return messageRecordRepository.findFirstByConversationIdAndRoleOrderByIdDesc(conversationId, role);
    }
}
