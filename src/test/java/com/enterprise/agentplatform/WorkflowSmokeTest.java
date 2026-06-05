package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.agentplatform.domain.entity.DocumentIndexTask;
import com.enterprise.agentplatform.domain.enums.DocumentIndexTaskStatus;
import com.enterprise.agentplatform.domain.repository.DocumentIndexTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentIndexTaskRepository documentIndexTaskRepository;

    @Test
    void shouldLoginCreateKnowledgeBaseUploadDocumentAndAnswerQuestion() throws Exception {
        String adminToken = login("admin", "admin123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken);
        Long documentId = uploadDocument(adminToken, knowledgeBaseId);
        waitForDocumentIndexed(adminToken, documentId);
        assertLatestIndexTask(documentId, DocumentIndexTaskStatus.SUCCESS);
        assertDocumentListed(adminToken, knowledgeBaseId, documentId, "SUCCESS");
        requestDocumentReindex(adminToken, documentId);
        waitForDocumentIndexed(adminToken, documentId);
        assertLatestIndexTask(documentId, DocumentIndexTaskStatus.SUCCESS);

        String userToken = login("user", "user123");
        mockMvc.perform(post("/api/v1/chat/ask")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "question": "VPN 无法连接应该怎么处理？"
                                }
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("账号状态")))
                .andExpect(jsonPath("$.data.citations[0].documentName").value("vpn-guide.md"))
                .andExpect(jsonPath("$.data.fallback").value(false));
    }

    @Test
    void shouldKeepDocumentFailureReasonWhenIndexingFails() throws Exception {
        String adminToken = login("admin", "admin123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken);
        Long documentId = uploadDocument(
                adminToken,
                knowledgeBaseId,
                "unsupported.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "XLSX content is not supported yet."
        );

        JsonNode failedDocument = waitForDocumentStatus(adminToken, documentId, "FAILED");

        assertThat(failedDocument.path("parseStatus").asText()).isEqualTo("FAILED");
        assertThat(failedDocument.path("indexStatus").asText()).isEqualTo("FAILED");
        assertThat(failedDocument.path("failureReason").asText()).contains("暂不支持该文件类型");
        assertThat(failedDocument.path("chunkCount").asLong()).isZero();
        assertLatestIndexTask(documentId, DocumentIndexTaskStatus.FAILED);
    }

    @Test
    void shouldUploadDocxDocumentAndAnswerWithExtractedText() throws Exception {
        String adminToken = login("admin", "admin123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken);
        Long documentId = uploadDocument(
                adminToken,
                knowledgeBaseId,
                "vpn-guide.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                minimalDocx("VPN 连接失败时，先确认账号状态，再检查客户端版本和网络连接。")
        );

        waitForDocumentIndexed(adminToken, documentId);
        JsonNode listedDocument = assertDocumentListed(adminToken, knowledgeBaseId, documentId, "SUCCESS");
        assertThat(listedDocument.path("fileName").asText()).isEqualTo("vpn-guide.docx");
        assertLatestIndexTask(documentId, DocumentIndexTaskStatus.SUCCESS);

        String userToken = login("user", "user123");
        mockMvc.perform(post("/api/v1/chat/ask")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "question": "VPN 无法连接应该怎么处理？"
                                }
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("账号状态")))
                .andExpect(jsonPath("$.data.citations[0].documentName").value("vpn-guide.docx"))
                .andExpect(jsonPath("$.data.fallback").value(false));
    }

    @Test
    void shouldListDocumentsAndReindexExistingDocument() throws Exception {
        String adminToken = login("admin", "admin123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken);
        Long documentId = uploadDocument(adminToken, knowledgeBaseId);
        waitForDocumentIndexed(adminToken, documentId);

        JsonNode listedDocument = assertDocumentListed(adminToken, knowledgeBaseId, documentId, "SUCCESS");
        assertThat(listedDocument.path("fileName").asText()).isEqualTo("vpn-guide.md");
        assertThat(listedDocument.path("chunkCount").asLong()).isGreaterThanOrEqualTo(1L);

        JsonNode reindexResponse = requestDocumentReindex(adminToken, documentId);
        assertThat(reindexResponse.path("parseStatus").asText()).isEqualTo("PENDING");
        assertThat(reindexResponse.path("indexStatus").asText()).isEqualTo("PENDING");

        JsonNode reindexedDocument = waitForDocumentStatus(adminToken, documentId, "SUCCESS");
        assertThat(reindexedDocument.path("failureReason").isNull()).isTrue();
        assertThat(reindexedDocument.path("chunkCount").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(documentIndexTaskRepository.findByDocumentIdOrderByIdDesc(documentId)).hasSizeGreaterThanOrEqualTo(2);
        assertLatestIndexTask(documentId, DocumentIndexTaskStatus.SUCCESS);
    }

    @Test
    void shouldFilterRetryFailedDocumentsAndDeleteIndexedDocument() throws Exception {
        String adminToken = login("admin", "admin123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken);
        Long indexedDocumentId = uploadDocument(adminToken, knowledgeBaseId);
        waitForDocumentIndexed(adminToken, indexedDocumentId);
        Long failedDocumentId = uploadDocument(
                adminToken,
                knowledgeBaseId,
                "unsupported.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "XLSX content is not supported yet."
        );
        waitForDocumentStatus(adminToken, failedDocumentId, "FAILED");

        JsonNode failedDocuments = listDocuments(adminToken, knowledgeBaseId, "unsupported", "FAILED");
        assertThat(failedDocuments.size()).isEqualTo(1);
        assertThat(failedDocuments.get(0).path("id").asLong()).isEqualTo(failedDocumentId);

        JsonNode retriedDocuments = requestRetryFailed(adminToken, knowledgeBaseId);
        assertThat(retriedDocuments.size()).isEqualTo(1);
        assertThat(retriedDocuments.get(0).path("id").asLong()).isEqualTo(failedDocumentId);
        assertThat(retriedDocuments.get(0).path("indexStatus").asText()).isEqualTo("PENDING");
        waitForDocumentStatus(adminToken, failedDocumentId, "FAILED");
        assertThat(documentIndexTaskRepository.findByDocumentIdOrderByIdDesc(failedDocumentId)).hasSizeGreaterThanOrEqualTo(2);
        assertLatestIndexTask(failedDocumentId, DocumentIndexTaskStatus.FAILED);

        deleteDocument(adminToken, indexedDocumentId);
        assertDocumentNotListed(adminToken, knowledgeBaseId, indexedDocumentId);
        mockMvc.perform(get("/api/v1/documents/{documentId}", indexedDocumentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
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
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }

    private Long createKnowledgeBase(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "IT Support KB",
                                  "description": "Support knowledge base"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    private Long uploadDocument(String adminToken, Long knowledgeBaseId) throws Exception {
        return uploadDocument(
                adminToken,
                knowledgeBaseId,
                "vpn-guide.md",
                "text/markdown",
                "VPN 连接失败时，先确认账号状态，再检查客户端版本和网络连接。"
        );
    }

    private Long uploadDocument(
            String adminToken,
            Long knowledgeBaseId,
            String fileName,
            String contentType,
            String content
    ) throws Exception {
        return uploadDocument(
                adminToken,
                knowledgeBaseId,
                fileName,
                contentType,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Long uploadDocument(
            String adminToken,
            Long knowledgeBaseId,
            String fileName,
            String contentType,
            byte[] content
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                content
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("knowledgeBaseId", knowledgeBaseId.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.indexStatus").value("PENDING"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    private byte[] minimalDocx(String text) throws Exception {
        String escapedText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """.formatted(escapedText);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            zipOutput.putNextEntry(new ZipEntry("word/document.xml"));
            zipOutput.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zipOutput.closeEntry();
        }
        return output.toByteArray();
    }

    private void waitForDocumentIndexed(String adminToken, Long documentId) throws Exception {
        JsonNode data = waitForDocumentStatus(adminToken, documentId, "SUCCESS");
        assertThat(data.path("parseStatus").asText()).isEqualTo("SUCCESS");
        assertThat(data.path("chunkCount").asLong()).isGreaterThanOrEqualTo(1L);
    }

    private JsonNode assertDocumentListed(
            String adminToken,
            Long knowledgeBaseId,
            Long documentId,
            String expectedIndexStatus
    ) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/documents")
                        .param("knowledgeBaseId", knowledgeBaseId.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode documents = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(documents).isNotEmpty();
        JsonNode listedDocument = null;
        for (JsonNode document : documents) {
            if (document.path("id").asLong() == documentId) {
                listedDocument = document;
                break;
            }
        }
        assertThat(listedDocument).isNotNull();
        assertThat(listedDocument.path("indexStatus").asText()).isEqualTo(expectedIndexStatus);
        return listedDocument;
    }

    private JsonNode listDocuments(
            String adminToken,
            Long knowledgeBaseId,
            String keyword,
            String indexStatus
    ) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/documents")
                        .param("knowledgeBaseId", knowledgeBaseId.toString())
                        .param("keyword", keyword)
                        .param("indexStatus", indexStatus)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode requestDocumentReindex(String adminToken, Long documentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/documents/{documentId}/reindex", documentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode requestRetryFailed(String adminToken, Long knowledgeBaseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/documents/retry-failed")
                        .param("knowledgeBaseId", knowledgeBaseId.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void deleteDocument(String adminToken, Long documentId) throws Exception {
        mockMvc.perform(delete("/api/v1/documents/{documentId}", documentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    private void assertDocumentNotListed(String adminToken, Long knowledgeBaseId, Long documentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/documents")
                        .param("knowledgeBaseId", knowledgeBaseId.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode documents = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        for (JsonNode document : documents) {
            assertThat(document.path("id").asLong()).isNotEqualTo(documentId);
        }
    }

    private JsonNode waitForDocumentStatus(String adminToken, Long documentId, String expectedIndexStatus) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
            if (expectedIndexStatus.equals(data.path("indexStatus").asText())) {
                return data;
            }
            if ("SUCCESS".equals(expectedIndexStatus) && "FAILED".equals(data.path("indexStatus").asText())) {
                throw new AssertionError("Document indexing failed: " + data.path("failureReason").asText());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Document indexing did not reach status " + expectedIndexStatus + " in time");
    }

    private void assertLatestIndexTask(Long documentId, DocumentIndexTaskStatus expectedStatus) {
        assertThat(documentIndexTaskRepository.findByDocumentIdOrderByIdDesc(documentId))
                .isNotEmpty();
        DocumentIndexTask latestTask = documentIndexTaskRepository.findByDocumentIdOrderByIdDesc(documentId).get(0);
        assertThat(latestTask.getStatus()).isEqualTo(expectedStatus);
        assertThat(latestTask.getAttemptCount()).isGreaterThanOrEqualTo(1);
    }
}
