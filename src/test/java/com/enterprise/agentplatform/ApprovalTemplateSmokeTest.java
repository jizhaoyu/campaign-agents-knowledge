package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.agentplatform.domain.entity.ApprovalTask;
import com.enterprise.agentplatform.domain.entity.AuditLog;
import com.enterprise.agentplatform.domain.entity.Conversation;
import com.enterprise.agentplatform.domain.entity.MessageRecord;
import com.enterprise.agentplatform.domain.entity.Ticket;
import com.enterprise.agentplatform.domain.enums.ConversationStatus;
import com.enterprise.agentplatform.domain.enums.MessageRole;
import com.enterprise.agentplatform.domain.enums.TicketStatus;
import com.enterprise.agentplatform.domain.repository.ApprovalTaskRepository;
import com.enterprise.agentplatform.domain.repository.AuditLogRepository;
import com.enterprise.agentplatform.domain.repository.ConversationRepository;
import com.enterprise.agentplatform.domain.repository.MessageRecordRepository;
import com.enterprise.agentplatform.domain.repository.TicketRepository;
import com.enterprise.agentplatform.ticket.dto.SubmitTicketResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ApprovalTemplateSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRecordRepository messageRecordRepository;

    @Autowired
    private ApprovalTaskRepository approvalTaskRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void shouldListApprovalCommentTemplatesAndApplyTemplateComment() throws Exception {
        String userToken = login("user", "user123");
        Long conversationId = createConversation(2L);
        SubmitTicketResponse submittedTicket = submitHighPriorityTicket(userToken, conversationId);
        String approverToken = login("approver", "approver123");

        mockMvc.perform(get("/api/v1/approvals/comment-templates")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'APPROVE_EVIDENCE_SUFFICIENT')]").exists())
                .andExpect(jsonPath("$.data[?(@.code == 'REJECT_EVIDENCE_INSUFFICIENT')]").exists());

        MvcResult approvalResult = mockMvc.perform(post("/api/v1/approvals/{id}/approve", submittedTicket.approvalTaskId())
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "APPROVE_EVIDENCE_SUFFICIENT",
                                  "comment": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.comment").value("已核对知识库引用和工单内容，证据充分，同意创建正式工单。"))
                .andReturn();

        JsonNode approvalData = objectMapper.readTree(approvalResult.getResponse().getContentAsString()).path("data");
        ApprovalTask task = approvalTaskRepository.findById(approvalData.path("id").asLong()).orElseThrow();
        assertThat(task.getComment()).isEqualTo("已核对知识库引用和工单内容，证据充分，同意创建正式工单。");

        Ticket ticket = ticketRepository.findById(submittedTicket.ticketId()).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);

        AuditLog auditLog = auditLogRepository
                .findFirstByEventTypeAndTargetTypeAndTargetIdOrderByIdDesc("APPROVAL_APPROVED", "TICKET", ticket.getId())
                .orElseThrow();
        assertThat(auditLog.getPayloadJson())
                .contains("\"templateCode\":\"APPROVE_EVIDENCE_SUFFICIENT\"")
                .contains("已核对知识库引用和工单内容");
    }

    @Test
    void shouldRejectTemplateThatDoesNotMatchDecisionAction() throws Exception {
        String userToken = login("user", "user123");
        Long conversationId = createConversation(2L);
        SubmitTicketResponse submittedTicket = submitHighPriorityTicket(userToken, conversationId);
        String approverToken = login("approver", "approver123");

        mockMvc.perform(post("/api/v1/approvals/{id}/reject", submittedTicket.approvalTaskId())
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "APPROVE_EVIDENCE_SUFFICIENT",
                                  "comment": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        ApprovalTask task = approvalTaskRepository.findById(submittedTicket.approvalTaskId()).orElseThrow();
        assertThat(task.getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void shouldRejectSecondDecisionAfterApprovalIsAlreadyDecided() throws Exception {
        String userToken = login("user", "user123");
        Long conversationId = createConversation(2L);
        SubmitTicketResponse submittedTicket = submitHighPriorityTicket(userToken, conversationId);
        String approverToken = login("approver", "approver123");

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", submittedTicket.approvalTaskId())
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "APPROVE_EVIDENCE_SUFFICIENT",
                                  "comment": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/approvals/{id}/reject", submittedTicket.approvalTaskId())
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "REJECT_EVIDENCE_INSUFFICIENT",
                                  "comment": ""
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_ALREADY_DECIDED"));

        ApprovalTask task = approvalTaskRepository.findById(submittedTicket.approvalTaskId()).orElseThrow();
        assertThat(task.getStatus().name()).isEqualTo("APPROVED");

        Ticket ticket = ticketRepository.findById(submittedTicket.ticketId()).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(auditLogRepository.findFirstByEventTypeAndTargetTypeAndTargetIdOrderByIdDesc(
                "APPROVAL_REJECTED",
                "TICKET",
                ticket.getId()
        )).isEmpty();
    }

    private SubmitTicketResponse submitHighPriorityTicket(String userToken, Long conversationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": %d,
                                  "title": "VPN 严重中断",
                                  "description": "VPN 无法连接影响远程办公，需要高优先级处理。",
                                  "priority": "HIGH",
                                  "assigneeId": 3
                                }
                                """.formatted(conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.approvalRequired").value(true))
                .andReturn();
        return objectMapper.treeToValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).path("data"),
                SubmitTicketResponse.class
        );
    }

    private Long createConversation(Long userId) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setKnowledgeBaseId(1L);
        conversation.setStatus(ConversationStatus.OPEN);
        conversation = conversationRepository.save(conversation);

        MessageRecord userMessage = new MessageRecord();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent("VPN 无法连接，影响远程办公。");
        messageRecordRepository.save(userMessage);

        MessageRecord assistantMessage = new MessageRecord();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setContent("请先确认账号状态，再检查客户端版本和网络连接。");
        assistantMessage.setCitationJson("[]");
        messageRecordRepository.save(assistantMessage);
        return conversation.getId();
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
