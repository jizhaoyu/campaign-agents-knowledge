package com.enterprise.agentplatform.approval.controller;

import com.enterprise.agentplatform.approval.dto.ApprovalDecisionRequest;
import com.enterprise.agentplatform.approval.dto.ApprovalCommentTemplateResponse;
import com.enterprise.agentplatform.approval.dto.ApprovalTaskResponse;
import com.enterprise.agentplatform.approval.service.ApprovalCommentTemplateService;
import com.enterprise.agentplatform.approval.service.ApprovalService;
import com.enterprise.agentplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ApprovalCommentTemplateService approvalCommentTemplateService;

    public ApprovalController(
            ApprovalService approvalService,
            ApprovalCommentTemplateService approvalCommentTemplateService
    ) {
        this.approvalService = approvalService;
        this.approvalCommentTemplateService = approvalCommentTemplateService;
    }

    @GetMapping("/comment-templates")
    @PreAuthorize("hasAuthority('approval:review')")
    public ApiResponse<List<ApprovalCommentTemplateResponse>> commentTemplates() {
        return ApiResponse.success(approvalCommentTemplateService.listTemplates());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('approval:review')")
    public ApiResponse<List<ApprovalTaskResponse>> pending() {
        return ApiResponse.success(approvalService.listPending());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('approval:review')")
    public ApiResponse<ApprovalTaskResponse> approve(
            @PathVariable("id") Long id,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        return ApiResponse.success(approvalService.approve(id, request.templateCode(), request.comment()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('approval:review')")
    public ApiResponse<ApprovalTaskResponse> reject(
            @PathVariable("id") Long id,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        return ApiResponse.success(approvalService.reject(id, request.templateCode(), request.comment()));
    }
}
