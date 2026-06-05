package com.enterprise.agentplatform.knowledge.service;

import com.enterprise.agentplatform.common.security.CurrentUserService;
import com.enterprise.agentplatform.domain.entity.KnowledgeBase;
import com.enterprise.agentplatform.domain.enums.ResourceStatus;
import com.enterprise.agentplatform.domain.repository.KnowledgeBaseRepository;
import com.enterprise.agentplatform.knowledge.dto.CreateKnowledgeBaseRequest;
import com.enterprise.agentplatform.knowledge.dto.KnowledgeBaseResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final CurrentUserService currentUserService;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository, CurrentUserService currentUserService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(request.name());
        knowledgeBase.setDescription(request.description());
        knowledgeBase.setStatus(ResourceStatus.ACTIVE);
        knowledgeBase.setCreatedBy(currentUserService.requireUserId());
        KnowledgeBase saved = knowledgeBaseRepository.save(knowledgeBase);
        return toResponse(saved);
    }

    public List<KnowledgeBaseResponse> list(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        List<KnowledgeBase> knowledgeBases = normalizedKeyword.isBlank()
                ? knowledgeBaseRepository.findAll()
                : knowledgeBaseRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                        normalizedKeyword,
                        normalizedKeyword
                );
        return knowledgeBases
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public KnowledgeBase requireExisting(Long id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new com.enterprise.agentplatform.common.exception.BusinessException(
                        com.enterprise.agentplatform.common.api.ErrorCode.RESOURCE_NOT_FOUND,
                        "知识库不存在: " + id
                ));
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getStatus().name()
        );
    }
}
