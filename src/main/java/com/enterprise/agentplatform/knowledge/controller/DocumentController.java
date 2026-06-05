package com.enterprise.agentplatform.knowledge.controller;

import com.enterprise.agentplatform.common.api.ApiResponse;
import com.enterprise.agentplatform.common.api.PageResponse;
import com.enterprise.agentplatform.common.api.PageRequestValidator;
import com.enterprise.agentplatform.knowledge.dto.DocumentUploadResponse;
import com.enterprise.agentplatform.knowledge.service.DocumentService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<DocumentUploadResponse> upload(
            @RequestParam("knowledgeBaseId") @NotNull Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(documentService.upload(knowledgeBaseId, file));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<?> list(
            @RequestParam("knowledgeBaseId") @NotNull Long knowledgeBaseId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "indexStatus", required = false) String indexStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        if (!PageRequestValidator.isPaged(page, size)) {
            return ApiResponse.success(documentService.listByKnowledgeBase(knowledgeBaseId, keyword, indexStatus));
        }
        PageRequestValidator.Params pageRequest = PageRequestValidator.resolve(page, size, 20);
        PageResponse<DocumentUploadResponse> documents = documentService.listByKnowledgeBase(
                knowledgeBaseId,
                keyword,
                indexStatus,
                pageRequest.page(),
                pageRequest.size()
        );
        return ApiResponse.success(documents);
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<DocumentUploadResponse> getStatus(@PathVariable Long documentId) {
        return ApiResponse.success(documentService.getStatus(documentId));
    }

    @PostMapping("/{documentId}/reindex")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<DocumentUploadResponse> reindex(@PathVariable Long documentId) {
        return ApiResponse.success(documentService.reindex(documentId));
    }

    @PostMapping("/retry-failed")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<List<DocumentUploadResponse>> retryFailed(
            @RequestParam("knowledgeBaseId") @NotNull Long knowledgeBaseId
    ) {
        return ApiResponse.success(documentService.retryFailed(knowledgeBaseId));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<Void> delete(@PathVariable Long documentId) {
        documentService.delete(documentId);
        return ApiResponse.success(null);
    }
}
