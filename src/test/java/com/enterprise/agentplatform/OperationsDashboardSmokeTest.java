package com.enterprise.agentplatform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.agentplatform.domain.entity.ApprovalTask;
import com.enterprise.agentplatform.domain.entity.AuthTokenSession;
import com.enterprise.agentplatform.domain.entity.DocumentIndexTask;
import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.entity.KnowledgeBase;
import com.enterprise.agentplatform.domain.entity.Ticket;
import com.enterprise.agentplatform.domain.enums.ApprovalStatus;
import com.enterprise.agentplatform.domain.enums.ApprovalTargetType;
import com.enterprise.agentplatform.domain.enums.DocumentIndexTaskStatus;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import com.enterprise.agentplatform.domain.enums.ResourceStatus;
import com.enterprise.agentplatform.domain.enums.TicketPriority;
import com.enterprise.agentplatform.domain.enums.TicketStatus;
import com.enterprise.agentplatform.domain.repository.ApprovalTaskRepository;
import com.enterprise.agentplatform.domain.repository.AuthTokenSessionRepository;
import com.enterprise.agentplatform.domain.repository.DocumentIndexTaskRepository;
import com.enterprise.agentplatform.domain.repository.DocumentRecordRepository;
import com.enterprise.agentplatform.domain.repository.KnowledgeBaseRepository;
import com.enterprise.agentplatform.domain.repository.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class OperationsDashboardSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private DocumentRecordRepository documentRecordRepository;

    @Autowired
    private DocumentIndexTaskRepository documentIndexTaskRepository;

    @Autowired
    private ApprovalTaskRepository approvalTaskRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AuthTokenSessionRepository authTokenSessionRepository;

    @Test
    void shouldSummarizeOperationsMetricsForDashboardReadersOnly() throws Exception {
        String adminToken = login("admin", "admin123");
        String userToken = login("user", "user123");

        long baselineKnowledgeBases = knowledgeBaseRepository.count();
        long baselineDocuments = documentRecordRepository.count();
        long baselinePendingTasks = documentIndexTaskRepository.countByStatus(DocumentIndexTaskStatus.PENDING);
        long baselineRunningTasks = documentIndexTaskRepository.countByStatus(DocumentIndexTaskStatus.RUNNING);
        long baselineFailedTasks = documentIndexTaskRepository.countByStatus(DocumentIndexTaskStatus.FAILED);
        long baselineFailedDocuments = documentRecordRepository.countByIndexStatus(ProcessingStatus.FAILED);
        long baselinePendingApprovals = approvalTaskRepository.countByStatus(ApprovalStatus.PENDING);
        long baselineActiveHighRiskTickets = ticketRepository.countByPriorityAndStatusIn(
                TicketPriority.HIGH,
                List.of(TicketStatus.PENDING_APPROVAL, TicketStatus.OPEN)
        );
        long baselinePendingHighRiskTickets = ticketRepository.countByPriorityAndStatus(
                TicketPriority.HIGH,
                TicketStatus.PENDING_APPROVAL
        );
        long baselineActiveSessions = authTokenSessionRepository.countActiveSessions(LocalDateTime.now());

        seedDashboardMetrics();

        mockMvc.perform(get("/api/v1/dashboard/operations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.knowledgeBaseCount").value(baselineKnowledgeBases + 1))
                .andExpect(jsonPath("$.data.documentCount").value(baselineDocuments + 2))
                .andExpect(jsonPath("$.data.pendingIndexTaskCount").value(baselinePendingTasks + 1))
                .andExpect(jsonPath("$.data.runningIndexTaskCount").value(baselineRunningTasks + 1))
                .andExpect(jsonPath("$.data.failedIndexTaskCount").value(baselineFailedTasks + 1))
                .andExpect(jsonPath("$.data.failedDocumentCount").value(baselineFailedDocuments + 1))
                .andExpect(jsonPath("$.data.pendingApprovalCount").value(baselinePendingApprovals + 1))
                .andExpect(jsonPath("$.data.activeHighRiskTicketCount").value(baselineActiveHighRiskTickets + 2))
                .andExpect(jsonPath("$.data.pendingHighRiskTicketCount").value(baselinePendingHighRiskTickets + 1))
                .andExpect(jsonPath("$.data.activeTokenSessionCount").value(baselineActiveSessions + 1))
                .andExpect(jsonPath("$.data.healthLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.data.alertCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.healthSummary").value(org.hamcrest.Matchers.containsString("立即处理")))
                .andExpect(jsonPath("$.data.recommendedActions.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.generatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/dashboard/operations")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    private void seedDashboardMetrics() {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName("Dashboard KB " + suffix.substring(0, 8));
        knowledgeBase.setDescription("Dashboard metrics smoke test data");
        knowledgeBase.setStatus(ResourceStatus.ACTIVE);
        knowledgeBase.setCreatedBy(1L);
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);

        DocumentRecord failedDocument = createDocument(knowledgeBase.getId(), "failed-" + suffix + ".md", ProcessingStatus.FAILED);
        DocumentRecord indexedDocument = createDocument(knowledgeBase.getId(), "indexed-" + suffix + ".md", ProcessingStatus.SUCCESS);

        createIndexTask(failedDocument.getId(), DocumentIndexTaskStatus.PENDING);
        createIndexTask(failedDocument.getId(), DocumentIndexTaskStatus.RUNNING);
        createIndexTask(indexedDocument.getId(), DocumentIndexTaskStatus.FAILED);

        Ticket openTicket = createTicket("Dashboard open high risk " + suffix, TicketStatus.OPEN);
        Ticket pendingTicket = createTicket("Dashboard pending high risk " + suffix, TicketStatus.PENDING_APPROVAL);

        ApprovalTask approvalTask = new ApprovalTask();
        approvalTask.setTargetType(ApprovalTargetType.TICKET_CREATE);
        approvalTask.setTargetId(pendingTicket.getId());
        approvalTask.setApproverId(4L);
        approvalTask.setStatus(ApprovalStatus.PENDING);
        approvalTaskRepository.save(approvalTask);

        AuthTokenSession session = new AuthTokenSession();
        session.setTokenHash(randomHash());
        session.setRefreshTokenHash(randomHash());
        session.setUserId(99_000L + Math.floorMod(System.nanoTime(), 1_000L));
        session.setUsername("dashboard_metric_" + suffix.substring(0, 8));
        session.setRoleCodes("ADMIN");
        session.setIssuedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusHours(2));
        session.setRefreshExpiresAt(LocalDateTime.now().plusDays(7));
        authTokenSessionRepository.save(session);

        openTicket.setAssigneeId(3L);
        ticketRepository.save(openTicket);
    }

    private DocumentRecord createDocument(Long knowledgeBaseId, String fileName, ProcessingStatus indexStatus) {
        DocumentRecord document = new DocumentRecord();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName(fileName);
        document.setFileType("md");
        document.setObjectKey("dashboard/" + fileName);
        document.setParseStatus(ProcessingStatus.SUCCESS);
        document.setIndexStatus(indexStatus);
        document.setUploadedBy(1L);
        document.setFailureReason(indexStatus == ProcessingStatus.FAILED ? "Smoke test failure" : null);
        return documentRecordRepository.save(document);
    }

    private void createIndexTask(Long documentId, DocumentIndexTaskStatus status) {
        DocumentIndexTask task = new DocumentIndexTask();
        task.setDocumentId(documentId);
        task.setStatus(status);
        task.setAttemptCount(status == DocumentIndexTaskStatus.FAILED ? 2 : 0);
        task.setMaxAttempts(2);
        task.setTraceId("dashboard-smoke-" + documentId + "-" + status);
        task.setLastError(status == DocumentIndexTaskStatus.FAILED ? "Smoke test task failure" : null);
        task.setNextRunAt(LocalDateTime.now().plusHours(1));
        task.setStartedAt(status == DocumentIndexTaskStatus.RUNNING ? LocalDateTime.now() : null);
        task.setFinishedAt(status == DocumentIndexTaskStatus.FAILED ? LocalDateTime.now() : null);
        documentIndexTaskRepository.save(task);
    }

    private Ticket createTicket(String title, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setConversationId(1L);
        ticket.setTitle(title);
        ticket.setDescription("High priority ticket used by operations dashboard smoke test.");
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(status);
        ticket.setCreatedBy(2L);
        return ticketRepository.save(ticket);
    }

    private String randomHash() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
