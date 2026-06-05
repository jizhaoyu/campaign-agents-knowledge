package com.enterprise.agentplatform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.entity.KnowledgeBase;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import com.enterprise.agentplatform.domain.enums.ResourceStatus;
import com.enterprise.agentplatform.domain.repository.DocumentRecordRepository;
import com.enterprise.agentplatform.domain.repository.KnowledgeBaseRepository;
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
class DocumentPaginationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private DocumentRecordRepository documentRecordRepository;

    @Test
    void shouldPageAndFilterDocumentsWithoutLoadingWholeKnowledgeBase() throws Exception {
        String adminToken = login("admin", "admin123");
        KnowledgeBase knowledgeBase = createKnowledgeBase();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        createDocument(knowledgeBase.getId(), "vpn-runbook-" + suffix + "-1.md", ProcessingStatus.SUCCESS);
        createDocument(knowledgeBase.getId(), "vpn-runbook-" + suffix + "-2.md", ProcessingStatus.SUCCESS);
        createDocument(knowledgeBase.getId(), "vpn-failure-" + suffix + ".md", ProcessingStatus.FAILED);

        mockMvc.perform(get("/api/v1/documents")
                        .param("knowledgeBaseId", knowledgeBase.getId().toString())
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasPrevious").value(false))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc.perform(get("/api/v1/documents")
                        .param("knowledgeBaseId", knowledgeBase.getId().toString())
                        .param("keyword", "failure-" + suffix)
                        .param("indexStatus", "FAILED")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].fileName").value("vpn-failure-" + suffix + ".md"))
                .andExpect(jsonPath("$.data.items[0].indexStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.totalItems").value(1));

        mockMvc.perform(get("/api/v1/documents")
                        .param("knowledgeBaseId", knowledgeBase.getId().toString())
                        .param("keyword", "failure-" + suffix)
                        .param("indexStatus", "FAILED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fileName").value("vpn-failure-" + suffix + ".md"))
                .andExpect(jsonPath("$.data[0].indexStatus").value("FAILED"));

        mockMvc.perform(get("/api/v1/documents")
                        .param("knowledgeBaseId", knowledgeBase.getId().toString())
                        .param("page", "-1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/documents")
                        .param("knowledgeBaseId", knowledgeBase.getId().toString())
                        .param("page", "0")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private KnowledgeBase createKnowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName("Document Page KB " + UUID.randomUUID().toString().substring(0, 8));
        knowledgeBase.setDescription("Pagination smoke test data");
        knowledgeBase.setStatus(ResourceStatus.ACTIVE);
        knowledgeBase.setCreatedBy(1L);
        return knowledgeBaseRepository.save(knowledgeBase);
    }

    private void createDocument(Long knowledgeBaseId, String fileName, ProcessingStatus indexStatus) {
        DocumentRecord document = new DocumentRecord();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName(fileName);
        document.setFileType("md");
        document.setObjectKey("pagination/" + fileName);
        document.setParseStatus(indexStatus);
        document.setIndexStatus(indexStatus);
        document.setUploadedBy(1L);
        document.setFailureReason(indexStatus == ProcessingStatus.FAILED ? "Pagination smoke test failure" : null);
        documentRecordRepository.save(document);
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
