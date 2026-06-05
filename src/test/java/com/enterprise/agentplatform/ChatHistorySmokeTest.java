package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ChatHistorySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldListOnlyCurrentUserRecentChatHistory() throws Exception {
        String adminToken = login("admin", "admin123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken);
        String userToken = login("user", "user123");
        String supportToken = login("support", "support123");

        Long olderConversationId = ask(userToken, knowledgeBaseId, "普通用户历史问题 A");
        Long latestConversationId = ask(userToken, knowledgeBaseId, "普通用户历史问题 B");
        Long supportConversationId = ask(supportToken, knowledgeBaseId, "支持工程师的私有问题");

        MvcResult historyResult = mockMvc.perform(get("/api/v1/chat/history")
                        .param("limit", "5")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].conversationId").value(latestConversationId))
                .andExpect(jsonPath("$.data[0].question").value("普通用户历史问题 B"))
                .andExpect(jsonPath("$.data[0].answer").value(org.hamcrest.Matchers.containsString("未找到足够证据")))
                .andExpect(jsonPath("$.data[0].confidence").value("NONE"))
                .andExpect(jsonPath("$.data[0].fallback").value(true))
                .andExpect(jsonPath("$.data[0].citations").isEmpty())
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andReturn();

        JsonNode history = objectMapper.readTree(historyResult.getResponse().getContentAsString()).path("data");
        assertThat(history)
                .extracting(item -> item.path("conversationId").asLong())
                .contains(latestConversationId, olderConversationId)
                .doesNotContain(supportConversationId);
    }

    @Test
    void shouldValidateHistoryLimit() throws Exception {
        String userToken = login("user", "user123");

        mockMvc.perform(get("/api/v1/chat/history")
                        .param("limit", "0")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Long createKnowledgeBase(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Chat History KB",
                                  "description": "History API test"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();
    }

    private Long ask(String token, Long knowledgeBaseId, String question) throws Exception {
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
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("conversationId")
                .asLong();
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
