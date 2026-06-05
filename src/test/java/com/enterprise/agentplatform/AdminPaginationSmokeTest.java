package com.enterprise.agentplatform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.agentplatform.domain.entity.AuditLog;
import com.enterprise.agentplatform.domain.entity.AuthTokenSession;
import com.enterprise.agentplatform.domain.repository.AuditLogRepository;
import com.enterprise.agentplatform.domain.repository.AuthTokenSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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
class AdminPaginationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuthTokenSessionRepository authTokenSessionRepository;

    @Test
    void shouldPageAuditUsersAndTokenSessionsForAdminScreens() throws Exception {
        String adminToken = login("admin", "admin123");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        AuditLog matchedAudit = createAudit("ADMIN_PAGE_" + suffix, "ADMIN_PAGE_TARGET", 91_001L);
        createAudit("ADMIN_PAGE_OTHER_" + suffix, "ADMIN_PAGE_TARGET", 91_002L);
        createTokenSession("admin_page_session_" + suffix + "_b");
        AuthTokenSession newestSession = createTokenSession("admin_page_session_" + suffix + "_a");

        mockMvc.perform(get("/api/v1/audits")
                        .param("eventType", matchedAudit.getEventType())
                        .param("targetType", matchedAudit.getTargetType())
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(matchedAudit.getId()))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1));

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.totalItems").isNumber())
                .andExpect(jsonPath("$.data.hasNext").isBoolean());

        mockMvc.perform(get("/api/v1/users/token-sessions")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(newestSession.getId()))
                .andExpect(jsonPath("$.data.items[0].tokenHash").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].tokenFingerprint").exists())
                .andExpect(jsonPath("$.data.hasNext").isBoolean());

        mockMvc.perform(get("/api/v1/users/token-sessions")
                        .param("page", "0")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private AuditLog createAudit(String eventType, String targetType, Long targetId) {
        AuditLog log = new AuditLog();
        log.setActorId(1L);
        log.setEventType(eventType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTraceId("trace-" + UUID.randomUUID());
        log.setPayloadJson("{}");
        return auditLogRepository.save(log);
    }

    private AuthTokenSession createTokenSession(String username) {
        LocalDateTime now = LocalDateTime.now();
        AuthTokenSession session = new AuthTokenSession();
        session.setTokenHash(UUID.randomUUID().toString().replace("-", ""));
        session.setRefreshTokenHash(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(1L);
        session.setUsername(username);
        session.setRoleCodes("ADMIN");
        session.setIssuedAt(now);
        session.setExpiresAt(now.plusHours(2));
        session.setRefreshExpiresAt(now.plusDays(7));
        return authTokenSessionRepository.save(session);
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
