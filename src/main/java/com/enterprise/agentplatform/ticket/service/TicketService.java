package com.enterprise.agentplatform.ticket.service;

import com.enterprise.agentplatform.audit.service.AuditService;
import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.exception.BusinessException;
import com.enterprise.agentplatform.common.security.CurrentUserService;
import com.enterprise.agentplatform.common.security.PermissionCode;
import com.enterprise.agentplatform.domain.entity.ApprovalTask;
import com.enterprise.agentplatform.domain.entity.Conversation;
import com.enterprise.agentplatform.domain.entity.MessageRecord;
import com.enterprise.agentplatform.domain.entity.Role;
import com.enterprise.agentplatform.domain.entity.Ticket;
import com.enterprise.agentplatform.domain.entity.UserRole;
import com.enterprise.agentplatform.domain.enums.ApprovalStatus;
import com.enterprise.agentplatform.domain.enums.ApprovalTargetType;
import com.enterprise.agentplatform.domain.enums.MessageRole;
import com.enterprise.agentplatform.domain.enums.RoleCode;
import com.enterprise.agentplatform.domain.enums.TicketPriority;
import com.enterprise.agentplatform.domain.enums.TicketStatus;
import com.enterprise.agentplatform.domain.repository.ApprovalTaskRepository;
import com.enterprise.agentplatform.domain.repository.ConversationRepository;
import com.enterprise.agentplatform.domain.repository.MessageRecordRepository;
import com.enterprise.agentplatform.domain.repository.RoleRepository;
import com.enterprise.agentplatform.domain.repository.TicketRepository;
import com.enterprise.agentplatform.domain.repository.UserRoleRepository;
import com.enterprise.agentplatform.ticket.dto.SimilarTicketResponse;
import com.enterprise.agentplatform.ticket.dto.SubmitTicketRequest;
import com.enterprise.agentplatform.ticket.dto.SubmitTicketResponse;
import com.enterprise.agentplatform.ticket.dto.TicketDraftResponse;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private static final int SIMILAR_TICKET_LIMIT = 5;
    private static final int SIMILAR_CANDIDATE_LIMIT = 50;
    private static final int SIMILAR_KEYWORD_LIMIT = 8;

    private final ConversationRepository conversationRepository;
    private final MessageRecordRepository messageRecordRepository;
    private final TicketRepository ticketRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public TicketService(
            ConversationRepository conversationRepository,
            MessageRecordRepository messageRecordRepository,
            TicketRepository ticketRepository,
            ApprovalTaskRepository approvalTaskRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            CurrentUserService currentUserService,
            AuditService auditService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRecordRepository = messageRecordRepository;
        this.ticketRepository = ticketRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    public TicketDraftResponse generateDraft(Long conversationId) {
        Conversation conversation = requireAccessibleConversation(conversationId);
        String question = latestMessage(conversation.getId(), MessageRole.USER)
                .map(MessageRecord::getContent)
                .orElse("未提供问题");
        String answer = latestMessage(conversation.getId(), MessageRole.ASSISTANT)
                .map(MessageRecord::getContent)
                .orElse("暂无回答");
        TicketPriority priority = inferPriority(question);
        Long suggestedAssigneeId = resolveSuggestedAssigneeId();
        return new TicketDraftResponse(
                conversationId,
                buildTitle(question),
                buildDescription(question, answer),
                priority.name(),
                suggestedAssigneeId
        );
    }

    public List<SimilarTicketResponse> searchSimilar(Long conversationId) {
        Conversation conversation = requireAccessibleConversation(conversationId);
        String question = latestMessage(conversation.getId(), MessageRole.USER)
                .map(MessageRecord::getContent)
                .orElse("");
        Set<String> keywords = tokenize(question);
        if (keywords.isEmpty()) {
            return List.of();
        }
        Map<Long, Ticket> candidateById = keywords.stream()
                .limit(SIMILAR_KEYWORD_LIMIT)
                .flatMap(keyword -> ticketRepository.findByTitleContainingIgnoreCaseOrderByIdDesc(
                        keyword,
                        PageRequest.of(0, SIMILAR_CANDIDATE_LIMIT)
                ).stream())
                .collect(Collectors.toMap(Ticket::getId, ticket -> ticket, (existing, ignored) -> existing));
        return candidateById.values().stream()
                .map(ticket -> new SimilarTicketResponse(
                        ticket.getId(),
                        ticket.getTitle(),
                        ticket.getPriority().name(),
                        ticket.getStatus().name(),
                        score(ticket.getTitle() + " " + ticket.getDescription(), keywords)
                ))
                .filter(response -> response.score() > 0)
                .sorted(Comparator.comparingInt(SimilarTicketResponse::score).reversed())
                .limit(SIMILAR_TICKET_LIMIT)
                .toList();
    }

    @Transactional
    public SubmitTicketResponse submit(SubmitTicketRequest request) {
        Conversation conversation = requireAccessibleConversation(request.conversationId());
        TicketPriority priority = parsePriority(request.priority());
        Ticket ticket = new Ticket();
        ticket.setConversationId(conversation.getId());
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setPriority(priority);
        ticket.setStatus(TicketStatus.DRAFT);
        ticket.setAssigneeId(request.assigneeId());
        ticket.setCreatedBy(currentUserService.requireUserId());
        ticket = ticketRepository.save(ticket);

        boolean approvalRequired = priority == TicketPriority.HIGH;
        Long approvalTaskId = null;
        if (approvalRequired) {
            ticket.setStatus(TicketStatus.PENDING_APPROVAL);
            ticketRepository.save(ticket);
            ApprovalTask task = new ApprovalTask();
            task.setTargetType(ApprovalTargetType.TICKET_CREATE);
            task.setTargetId(ticket.getId());
            task.setApproverId(resolveApproverId());
            task.setStatus(ApprovalStatus.PENDING);
            task = approvalTaskRepository.save(task);
            approvalTaskId = task.getId();
            auditService.record("APPROVAL_CREATED", "TICKET", ticket.getId(), java.util.Map.of("approvalTaskId", approvalTaskId));
        } else {
            ticket.setStatus(TicketStatus.OPEN);
            ticketRepository.save(ticket);
        }

        auditService.record("TICKET_DRAFT_GENERATED", "TICKET", ticket.getId(), java.util.Map.of(
                "approvalRequired", approvalRequired,
                "priority", priority.name()
        ));

        return new SubmitTicketResponse(ticket.getId(), ticket.getStatus().name(), approvalRequired, approvalTaskId);
    }

    private Conversation requireAccessibleConversation(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在: " + conversationId));
        Long currentUserId = currentUserService.requireUserId();
        boolean privileged = currentUserService.hasPermission(PermissionCode.AUDIT_READ)
                || currentUserService.hasRole(RoleCode.SUPPORT.name());
        if (!privileged && !conversation.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该会话");
        }
        return conversation;
    }

    private Optional<MessageRecord> latestMessage(Long conversationId, MessageRole role) {
        return messageRecordRepository.findFirstByConversationIdAndRoleOrderByIdDesc(conversationId, role);
    }

    private TicketPriority inferPriority(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "无法", "中断", "失败", "严重", "error", "urgent", "down")) {
            return TicketPriority.HIGH;
        }
        if (containsAny(lower, "慢", "warning", "提示", "异常")) {
            return TicketPriority.MEDIUM;
        }
        return TicketPriority.LOW;
    }

    private boolean containsAny(String source, String... candidates) {
        for (String candidate : candidates) {
            if (source.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String buildTitle(String question) {
        String title = question.strip();
        if (title.length() <= 40) {
            return "【智能草稿】" + title;
        }
        return "【智能草稿】" + title.substring(0, 40) + "...";
    }

    private String buildDescription(String question, String answer) {
        return "问题描述:\n" + question + "\n\n知识库建议:\n" + answer;
    }

    private Set<String> tokenize(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsAlphabetic}\\p{IsIdeographic}\\p{IsDigit}]+", " ");
        return java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private int score(String candidate, Set<String> keywords) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                score++;
            }
        }
        return score;
    }

    private TicketPriority parsePriority(String value) {
        try {
            return TicketPriority.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "priority 非法: " + value);
        }
    }

    private Long resolveSuggestedAssigneeId() {
        return roleRepository.findByCode(RoleCode.SUPPORT.name())
                .map(Role::getId)
                .map(userRoleRepository::findByRoleId)
                .orElse(List.of())
                .stream()
                .map(UserRole::getUserId)
                .findFirst()
                .orElse(null);
    }

    private Long resolveApproverId() {
        return roleRepository.findByCode(RoleCode.APPROVER.name())
                .map(Role::getId)
                .map(userRoleRepository::findByRoleId)
                .orElse(List.of())
                .stream()
                .map(UserRole::getUserId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "未找到审批人"));
    }
}
