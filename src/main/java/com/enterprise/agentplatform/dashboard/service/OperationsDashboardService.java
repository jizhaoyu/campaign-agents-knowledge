package com.enterprise.agentplatform.dashboard.service;

import com.enterprise.agentplatform.dashboard.dto.OperationsDashboardResponse;
import com.enterprise.agentplatform.domain.enums.ApprovalStatus;
import com.enterprise.agentplatform.domain.enums.DocumentIndexTaskStatus;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import com.enterprise.agentplatform.domain.enums.TicketPriority;
import com.enterprise.agentplatform.domain.enums.TicketStatus;
import com.enterprise.agentplatform.domain.repository.ApprovalTaskRepository;
import com.enterprise.agentplatform.domain.repository.AuthTokenSessionRepository;
import com.enterprise.agentplatform.domain.repository.DocumentIndexTaskRepository;
import com.enterprise.agentplatform.domain.repository.DocumentRecordRepository;
import com.enterprise.agentplatform.domain.repository.KnowledgeBaseRepository;
import com.enterprise.agentplatform.domain.repository.TicketRepository;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OperationsDashboardService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRecordRepository documentRecordRepository;
    private final DocumentIndexTaskRepository documentIndexTaskRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final TicketRepository ticketRepository;
    private final AuthTokenSessionRepository authTokenSessionRepository;

    public OperationsDashboardService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRecordRepository documentRecordRepository,
            DocumentIndexTaskRepository documentIndexTaskRepository,
            ApprovalTaskRepository approvalTaskRepository,
            TicketRepository ticketRepository,
            AuthTokenSessionRepository authTokenSessionRepository
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRecordRepository = documentRecordRepository;
        this.documentIndexTaskRepository = documentIndexTaskRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.ticketRepository = ticketRepository;
        this.authTokenSessionRepository = authTokenSessionRepository;
    }

    public OperationsDashboardResponse summarize() {
        LocalDateTime now = LocalDateTime.now();
        long knowledgeBaseCount = knowledgeBaseRepository.count();
        long documentCount = documentRecordRepository.count();
        long pendingIndexTaskCount = documentIndexTaskRepository.countByStatus(DocumentIndexTaskStatus.PENDING);
        long runningIndexTaskCount = documentIndexTaskRepository.countByStatus(DocumentIndexTaskStatus.RUNNING);
        long failedIndexTaskCount = documentIndexTaskRepository.countByStatus(DocumentIndexTaskStatus.FAILED);
        long failedDocumentCount = documentRecordRepository.countByIndexStatus(ProcessingStatus.FAILED);
        long pendingApprovalCount = approvalTaskRepository.countByStatus(ApprovalStatus.PENDING);
        long pendingHighRiskTickets = ticketRepository.countByPriorityAndStatus(
                TicketPriority.HIGH,
                TicketStatus.PENDING_APPROVAL
        );
        long activeHighRiskTickets = ticketRepository.countByPriorityAndStatusIn(
                TicketPriority.HIGH,
                List.of(TicketStatus.PENDING_APPROVAL, TicketStatus.OPEN)
        );
        long activeTokenSessionCount = authTokenSessionRepository.countActiveSessions(now);
        HealthSnapshot health = summarizeHealth(
                pendingIndexTaskCount,
                runningIndexTaskCount,
                failedIndexTaskCount,
                failedDocumentCount,
                pendingApprovalCount,
                activeHighRiskTickets,
                pendingHighRiskTickets
        );

        return new OperationsDashboardResponse(
                knowledgeBaseCount,
                documentCount,
                pendingIndexTaskCount,
                runningIndexTaskCount,
                failedIndexTaskCount,
                failedDocumentCount,
                pendingApprovalCount,
                activeHighRiskTickets,
                pendingHighRiskTickets,
                activeTokenSessionCount,
                health.level(),
                health.alertCount(),
                health.summary(),
                health.recommendedActions(),
                now
        );
    }

    private HealthSnapshot summarizeHealth(
            long pendingIndexTaskCount,
            long runningIndexTaskCount,
            long failedIndexTaskCount,
            long failedDocumentCount,
            long pendingApprovalCount,
            long activeHighRiskTickets,
            long pendingHighRiskTickets
    ) {
        List<String> actions = new ArrayList<>();
        if (failedIndexTaskCount > 0 || failedDocumentCount > 0) {
            actions.add("处理失败索引任务和失败文档，优先查看失败原因后重试或修正文档格式。");
        }
        if (pendingIndexTaskCount > 10 && runningIndexTaskCount == 0) {
            actions.add("索引队列堆积且没有运行任务，请检查后台 worker 是否正常领取任务。");
        } else if (pendingIndexTaskCount > 10) {
            actions.add("索引队列堆积较多，请评估是否需要提高 worker 并发或拆分批次。");
        }
        if (pendingApprovalCount > 0) {
            actions.add("存在待审批任务，请安排审批人处理以免高风险工单阻塞。");
        }
        if (pendingHighRiskTickets > 0) {
            actions.add("存在待审批高风险工单，请优先核对证据并完成审批。");
        } else if (activeHighRiskTickets > 0) {
            actions.add("存在高风险开放工单，请跟踪处理进度和 SLA。");
        }

        long alertCount = actions.size();
        if (alertCount == 0) {
            return new HealthSnapshot("HEALTHY", 0, "当前无待处理运营告警。", List.of("保持索引队列、审批队列和高风险工单的日常巡检。"));
        }
        if (failedIndexTaskCount > 0 || failedDocumentCount > 0 || pendingHighRiskTickets > 0) {
            return new HealthSnapshot("CRITICAL", alertCount, "存在需要立即处理的索引失败或高风险阻塞项。", actions);
        }
        return new HealthSnapshot("ATTENTION", alertCount, "存在需要跟进的运营待办。", actions);
    }

    private record HealthSnapshot(
            String level,
            long alertCount,
            String summary,
            List<String> recommendedActions
    ) {
    }
}
