package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.rag.min-citation-count=2",
        "app.rag.min-top-score=10.0"
})
@AutoConfigureMockMvc
class RagRefusalPolicySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRefuseWhenNoEvidenceExists() throws Exception {
        String userToken = login("user", "user123");

        mockMvc.perform(post("/api/v1/chat/ask")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": 1,
                                  "question": "完全不存在的火星打印机错误码 XZ-9988 怎么处理？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.confidence").value("NONE"))
                .andExpect(jsonPath("$.data.citations").isEmpty())
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("未找到足够证据")));
    }

    @Test
    void shouldRefuseWeakEvidenceWhenThresholdsAreHigh() throws Exception {
        String adminToken = login("admin", "admin123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken);
        Long documentId = uploadDocument(adminToken, knowledgeBaseId);
        waitForDocumentIndexed(adminToken, documentId);
        String userToken = login("user", "user123");

        mockMvc.perform(post("/api/v1/chat/ask")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "question": "VPN 应该怎么处理？"
                                }
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.confidence").value("NONE"))
                .andExpect(jsonPath("$.data.citations").isEmpty())
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("未找到足够证据")));
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

    private Long createKnowledgeBase(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "RAG Refusal KB",
                                  "description": "Refusal policy test"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private Long uploadDocument(String adminToken, Long knowledgeBaseId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "vpn-refusal.md",
                "text/markdown",
                "VPN 连接失败时，先确认账号状态，再检查客户端版本和网络连接。".getBytes(StandardCharsets.UTF_8)
        );
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("knowledgeBaseId", knowledgeBaseId.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void waitForDocumentIndexed(String adminToken, Long documentId) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
            if ("SUCCESS".equals(data.path("indexStatus").asText())) {
                assertThat(data.path("chunkCount").asLong()).isGreaterThanOrEqualTo(1L);
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Document indexing did not finish in time");
    }
}
