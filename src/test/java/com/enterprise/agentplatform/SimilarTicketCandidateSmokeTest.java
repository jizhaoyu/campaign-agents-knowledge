package com.enterprise.agentplatform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.agentplatform.domain.entity.Ticket;
import com.enterprise.agentplatform.domain.enums.TicketPriority;
import com.enterprise.agentplatform.domain.enums.TicketStatus;
import com.enterprise.agentplatform.domain.repository.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class SimilarTicketCandidateSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketRepository ticketRepository;

    @Test
    void shouldFindSimilarTicketsFromBoundedKeywordCandidates() throws Exception {
        String adminToken = login("admin", "admin123");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long knowledgeBaseId = createKnowledgeBase(adminToken, "Similar KB " + suffix);
        Long conversationId = askQuestion(adminToken, knowledgeBaseId, "VPN " + suffix + " 无法连接，客户端报错");
        Ticket matchedTicket = createTicket("VPN " + suffix + " 历史故障", "客户端无法连接，检查账号状态和网络连接。");
        createTicket("打印机耗材提醒 " + suffix, "办公区打印机需要补充耗材。");

        mockMvc.perform(get("/api/v1/tickets/similar")
                        .param("conversationId", conversationId.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ticketId").value(matchedTicket.getId()))
                .andExpect(jsonPath("$.data[0].score").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    private Ticket createTicket(String title, String description) {
        Ticket ticket = new Ticket();
        ticket.setConversationId(1L);
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCreatedBy(1L);
        return ticketRepository.save(ticket);
    }

    private Long createKnowledgeBase(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "Similar ticket candidate smoke test"
                                }
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();
    }

    private Long askQuestion(String token, Long knowledgeBaseId, String question) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chat/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "question": "%s"
                                }
                                """.formatted(knowledgeBaseId, question)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("conversationId").asLong();
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
