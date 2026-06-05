package com.enterprise.agentplatform.knowledge.controller;

import com.enterprise.agentplatform.common.api.ApiResponse;
import com.enterprise.agentplatform.knowledge.dto.CreateKnowledgeBaseRequest;
import com.enterprise.agentplatform.knowledge.dto.KnowledgeBaseResponse;
import com.enterprise.agentplatform.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<KnowledgeBaseResponse> create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseService.create(request));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(knowledgeBaseService.list(keyword));
    }
}
