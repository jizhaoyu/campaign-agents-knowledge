package com.enterprise.agentplatform.approval.service;

import com.enterprise.agentplatform.audit.service.AuditService;
import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.exception.BusinessException;
import com.enterprise.agentplatform.common.security.CurrentUserService;
import com.enterprise.agentplatform.common.security.PermissionCode;
import com.enterprise.agentplatform.domain.entity.ApprovalTask;
import com.enterprise.agentplatform.domain.entity.Ticket;
import com.enterprise.agentplatform.domain.enums.ApprovalStatus;
import com.enterprise.agentplatform.domain.enums.TicketStatus;
import com.enterprise.agentplatform.domain.repository.ApprovalTaskRepository;
import com.enterprise.agentplatform.domain.repository.TicketRepository;
import com.enterprise.agentplatform.approval.dto.ApprovalTaskResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private final ApprovalTaskRepository approvalTaskRepository;
    private final TicketRepository ticketRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final ApprovalCommentTemplateService approvalCommentTemplateService;

    public ApprovalService(
            ApprovalTaskRepository approvalTaskRepository,
            TicketRepository ticketRepository,
            CurrentUserService currentUserService,
            AuditService auditService,
            ApprovalCommentTemplateService approvalCommentTemplateService
    ) {
        this.approvalTaskRepository = approvalTaskRepository;
        this.ticketRepository = ticketRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.approvalCommentTemplateService = approvalCommentTemplateService;
    }

    public List<ApprovalTaskResponse> listPending() {
        List<ApprovalTask> tasks;
        if (currentUserService.hasPermission(PermissionCode.AUDIT_READ)) {
            tasks = approvalTaskRepository.findByStatusOrderByIdDesc(ApprovalStatus.PENDING);
        } else {
            tasks = approvalTaskRepository.findByApproverIdAndStatusOrderByIdDesc(
                    currentUserService.requireUserId(),
                    ApprovalStatus.PENDING
            );
        }
        return tasks.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ApprovalTaskResponse approve(Long approvalId, String templateCode, String comment) {
        return decide(approvalId, true, templateCode, comment);
    }

    @Transactional
    public ApprovalTaskResponse reject(Long approvalId, String templateCode, String comment) {
        return decide(approvalId, false, templateCode, comment);
    }

    private ApprovalTaskResponse decide(Long approvalId, boolean approve, String templateCode, String comment) {
        ApprovalTask task = approvalTaskRepository.findLockedById(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND, "审批任务不存在"));

        Long currentUserId = currentUserService.requireUserId();
        boolean admin = currentUserService.hasPermission(PermissionCode.AUDIT_READ);
        if (!admin && !task.getApproverId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权审批该任务");
        }
        if (task.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_DECIDED, "审批任务已处理");
        }

        String action = approve ? "approve" : "reject";
        String resolvedTemplateCode = approvalCommentTemplateService.normalizeTemplateCode(action, templateCode);
        String resolvedComment = approvalCommentTemplateService.resolveComment(action, resolvedTemplateCode, comment);

        task.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        task.setComment(resolvedComment);
        task.setDecidedAt(LocalDateTime.now());
        approvalTaskRepository.save(task);

        Ticket ticket = ticketRepository.findById(task.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工单不存在"));
        ticket.setStatus(approve ? TicketStatus.OPEN : TicketStatus.REJECTED);
        ticketRepository.save(ticket);

        auditService.record(
                approve ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED",
                "TICKET",
                ticket.getId(),
                java.util.Map.of(
                        "approvalId", task.getId(),
                        "templateCode", resolvedTemplateCode == null ? "" : resolvedTemplateCode,
                        "comment", resolvedComment
                )
        );

        return toResponse(task);
    }

    private ApprovalTaskResponse toResponse(ApprovalTask task) {
        return new ApprovalTaskResponse(
                task.getId(),
                task.getTargetType().name(),
                task.getTargetId(),
                task.getApproverId(),
                task.getStatus().name(),
                task.getComment()
        );
    }
}
