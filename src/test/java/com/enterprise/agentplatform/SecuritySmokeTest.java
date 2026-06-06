package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.agentplatform.common.security.SimpleTokenStore;
import com.enterprise.agentplatform.common.security.TokenPrincipal;
import com.enterprise.agentplatform.common.security.RolePermissionMapper;
import com.enterprise.agentplatform.domain.entity.AuditLog;
import com.enterprise.agentplatform.domain.entity.AuthTokenSession;
import com.enterprise.agentplatform.domain.entity.UserAccount;
import com.enterprise.agentplatform.domain.repository.AuditLogRepository;
import com.enterprise.agentplatform.domain.repository.AuthTokenSessionRepository;
import com.enterprise.agentplatform.domain.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class SecuritySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthTokenSessionRepository authTokenSessionRepository;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldAuthenticateSeedUsersWithBcryptPasswords() throws Exception {
        for (Map.Entry<String, String> credential : Map.of(
                "admin", "admin123",
                "user", "user123",
                "support", "support123",
                "approver", "approver123"
        ).entrySet()) {
            JsonNode data = objectMapper.readTree(loginResult(credential.getKey(), credential.getValue())
                            .getResponse()
                            .getContentAsString())
                    .path("data");

            assertThat(data.path("accessToken").asText()).isNotBlank();
            assertThat(data.path("username").asText()).isEqualTo(credential.getKey());
            assertThat(data.path("permissions")).isNotEmpty();
        }
    }

    @Test
    void shouldPersistOnlyTokenHashAndResolveAfterStoreRecreation() throws Exception {
        JsonNode data = objectMapper.readTree(loginResult("admin", "admin123")
                        .getResponse()
                        .getContentAsString())
                .path("data");
        String token = data.path("accessToken").asText();

        AuthTokenSession session = authTokenSessionRepository.findByUsernameOrderByIdDesc("admin").get(0);

        assertThat(session.getTokenHash()).hasSize(64);
        assertThat(session.getTokenHash()).doesNotContain(token);
        assertThat(session.getRefreshTokenHash()).hasSize(64);
        assertThat(session.getRefreshExpiresAt()).isNotNull();
        assertThat(session.getRoleCodes()).contains("ADMIN");

        SimpleTokenStore recreatedStore = new SimpleTokenStore(authTokenSessionRepository, rolePermissionMapper, 7200, 604800);
        Optional<TokenPrincipal> resolvedPrincipal = new TransactionTemplate(transactionManager)
                .execute(status -> recreatedStore.resolve(token));
        assertThat(resolvedPrincipal)
                .isPresent()
                .get()
                .extracting(principal -> principal.username())
                .isEqualTo("admin");
        assertThat(resolvedPrincipal.orElseThrow().permissions()).contains("knowledge:manage", "audit:read");
    }

    @Test
    void shouldRefreshAccessTokenAndRotateRefreshToken() throws Exception {
        JsonNode loginData = objectMapper.readTree(loginResult("support", "support123").getResponse().getContentAsString())
                .path("data");
        String oldAccessToken = loginData.path("accessToken").asText();
        String oldRefreshToken = loginData.path("refreshToken").asText();
        AuthTokenSession sessionBeforeRefresh = authTokenSessionRepository.findByUsernameOrderByIdDesc("support").get(0);

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.refreshExpiresIn").value(604800))
                .andReturn();

        JsonNode refreshData = objectMapper.readTree(refreshResult.getResponse().getContentAsString()).path("data");
        String newAccessToken = refreshData.path("accessToken").asText();
        String newRefreshToken = refreshData.path("refreshToken").asText();
        assertThat(newAccessToken).isNotEqualTo(oldAccessToken);
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        AuthTokenSession sessionAfterRefresh = authTokenSessionRepository.findByUsernameOrderByIdDesc("support").get(0);
        assertThat(sessionAfterRefresh.getId()).isEqualTo(sessionBeforeRefresh.getId());
        assertThat(sessionAfterRefresh.getTokenHash()).hasSize(64).doesNotContain(newAccessToken);
        assertThat(sessionAfterRefresh.getRefreshTokenHash()).hasSize(64).doesNotContain(newRefreshToken);
        assertThat(sessionAfterRefresh.getLastRefreshedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(latestAudit("USER_TOKEN_REFRESHED", "AUTH_TOKEN_SESSION", sessionAfterRefresh.getId()))
                .isNotNull()
                .extracting(AuditLog::getActorId)
                .isEqualTo(sessionAfterRefresh.getUserId());
    }

    @Test
    void shouldAcceptOnlyOneConcurrentRefreshForSameToken() throws Exception {
        long refreshUserId = 50_000L + Math.floorMod(System.nanoTime(), 1_000_000L);
        String username = "refresh_race_user_" + refreshUserId;
        insertTestUser(refreshUserId, username, "{noop}refresh123", 0, false);
        JsonNode loginData = objectMapper.readTree(loginResult(username, "refresh123").getResponse().getContentAsString())
                .path("data");
        String refreshToken = loginData.path("refreshToken").asText();

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> refreshCall = () -> {
                if (!startGate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("refresh race did not start");
                }
                return mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "%s"
                                        }
                                        """.formatted(refreshToken)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Future<Integer> firstRefresh = executor.submit(refreshCall);
            Future<Integer> secondRefresh = executor.submit(refreshCall);
            startGate.countDown();

            List<Integer> statuses = List.of(
                    firstRefresh.get(10, TimeUnit.SECONDS),
                    secondRefresh.get(10, TimeUnit.SECONDS)
            );

            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
            AuthTokenSession refreshedSession = authTokenSessionRepository.findByUsernameOrderByIdDesc(username).get(0);
            assertThat(refreshedSession.getLastRefreshedAt()).isNotNull();
            assertThat(latestAudit("USER_TOKEN_REFRESHED", "AUTH_TOKEN_SESSION", refreshedSession.getId()))
                    .isNotNull()
                    .extracting(AuditLog::getActorId)
                    .isEqualTo(refreshUserId);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldLogoutAndRejectRevokedToken() throws Exception {
        String token = objectMapper.readTree(loginResult("admin", "admin123").getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        AuthTokenSession session = authTokenSessionRepository.findByUsernameOrderByIdDesc("admin").get(0);
        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(latestAudit("USER_LOGOUT", "USER", session.getUserId()))
                .isNotNull()
                .extracting(AuditLog::getActorId)
                .isEqualTo(session.getUserId());

        mockMvc.perform(get("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRecordSuccessfulLoginAuditWithoutSensitiveValues() throws Exception {
        long loginAuditUserId = 40_000L + Math.floorMod(System.nanoTime(), 1_000_000L);
        String username = "login_audit_user_" + loginAuditUserId;
        insertTestUser(loginAuditUserId, username, "{noop}login123", 0, false);

        String response = loginResult(username, "login123").getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(response).path("data").path("accessToken").asText();

        AuditLog auditLog = latestAudit("USER_LOGIN_SUCCEEDED", "USER", loginAuditUserId);
        assertThat(auditLog).isNotNull();
        assertThat(auditLog.getActorId()).isEqualTo(loginAuditUserId);
        assertThat(auditLog.getPayloadJson())
                .contains(username)
                .doesNotContain(accessToken)
                .doesNotContain("login123")
                .doesNotContain("password");
    }

    @Test
    void shouldLockAccountAfterRepeatedFailedLogins() throws Exception {
        long lockoutUserId = 10_000L + Math.floorMod(System.nanoTime(), 1_000_000L);
        String username = "lockout_user_" + lockoutUserId;
        insertTestUser(lockoutUserId, username, "{noop}lockout123", 0, false);

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "%s",
                                      "password": "wrong-password"
                                    }
                                    """.formatted(username)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        UserAccount lockedUser = userAccountRepository.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(lockedUser.getFailedLoginCount()).isEqualTo(5);
        assertThat(lockedUser.getLockedUntil()).isNotNull();
        assertThat(latestAudit("USER_LOGIN_FAILED", "USER", lockoutUserId))
                .isNotNull()
                .extracting(AuditLog::getPayloadJson)
                .asString()
                .contains("\"reason\":\"BAD_CREDENTIALS\"", "\"locked\":true")
                .doesNotContain("wrong-password");
        assertThat(latestAudit("USER_LOGIN_LOCKED", "USER", lockoutUserId))
                .isNotNull()
                .extracting(AuditLog::getPayloadJson)
                .asString()
                .contains("\"reason\":\"MAX_FAILED_ATTEMPTS\"");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "lockout123"
                                }
                                """.formatted(username)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertThat(latestAudit("USER_LOGIN_LOCKED", "USER", lockoutUserId))
                .isNotNull()
                .extracting(AuditLog::getPayloadJson)
                .asString()
                .contains("LOCK_STILL_ACTIVE");
    }

    @Test
    void shouldCountConcurrentFailedLoginsWithoutLostUpdates() throws Exception {
        long raceUserId = 60_000L + Math.floorMod(System.nanoTime(), 1_000_000L);
        String username = "failed_login_race_user_" + raceUserId;
        insertTestUser(raceUserId, username, "{noop}race123", 3, false);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> failedLoginCall = () -> {
                if (!startGate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("failed login race did not start");
                }
                return mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "%s",
                                          "password": "wrong-password"
                                        }
                                        """.formatted(username)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Future<Integer> firstLogin = executor.submit(failedLoginCall);
            Future<Integer> secondLogin = executor.submit(failedLoginCall);
            startGate.countDown();

            List<Integer> statuses = List.of(
                    firstLogin.get(10, TimeUnit.SECONDS),
                    secondLogin.get(10, TimeUnit.SECONDS)
            );

            assertThat(statuses).containsOnly(401);
            UserAccount lockedUser = userAccountRepository.findByUsernameIgnoreCase(username).orElseThrow();
            assertThat(lockedUser.getFailedLoginCount()).isEqualTo(5);
            assertThat(lockedUser.getLockedUntil()).isNotNull();
            assertThat(latestAudit("USER_LOGIN_LOCKED", "USER", raceUserId))
                    .isNotNull()
                    .extracting(AuditLog::getPayloadJson)
                    .asString()
                    .contains("\"failedLoginCount\":5");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAllowAdminToListAndUnlockUsers() throws Exception {
        long lockedUserId = 20_000L + Math.floorMod(System.nanoTime(), 1_000_000L);
        String username = "admin_unlock_user_" + lockedUserId;
        insertTestUser(lockedUserId, username, "{noop}unlock123", 5, true);
        String adminToken = objectMapper.readTree(loginResult("admin", "admin123").getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.username == '%s')]".formatted(username)).exists())
                .andExpect(jsonPath("$.data[?(@.username == '%s')].passwordHash".formatted(username)).doesNotExist());

        MvcResult unlockResult = mockMvc.perform(post("/api/v1/users/{userId}/unlock", lockedUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.failedLoginCount").value(0))
                .andReturn();
        JsonNode unlockedData = objectMapper.readTree(unlockResult.getResponse().getContentAsString()).path("data");
        assertThat(unlockedData.path("lockedUntil").isNull()).isTrue();

        UserAccount unlockedUser = userAccountRepository.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(unlockedUser.getFailedLoginCount()).isZero();
        assertThat(unlockedUser.getLockedUntil()).isNull();

        mockMvc.perform(get("/api/v1/audits")
                        .queryParam("eventType", "USER_UNLOCKED")
                        .queryParam("targetId", String.valueOf(lockedUserId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("USER_UNLOCKED"))
                .andExpect(jsonPath("$.data[0].targetType").value("USER"));
    }

    @Test
    void shouldAllowAdminToRevokeTokenSessions() throws Exception {
        String tokenToRevoke = objectMapper.readTree(loginResult("support", "support123").getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        AuthTokenSession session = authTokenSessionRepository.findByUsernameOrderByIdDesc("support").get(0);
        String adminToken = objectMapper.readTree(loginResult("admin", "admin123").getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(get("/api/v1/users/token-sessions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(session.getId())).exists())
                .andExpect(jsonPath("$.data[?(@.id == %d)].tokenHash".formatted(session.getId())).doesNotExist())
                .andExpect(jsonPath("$.data[?(@.id == %d)].tokenFingerprint".formatted(session.getId())).exists());

        mockMvc.perform(post("/api/v1/users/token-sessions/{sessionId}/revoke", session.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(session.getId()))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.revokedAt").exists());

        mockMvc.perform(get("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + tokenToRevoke))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/audits")
                        .queryParam("eventType", "TOKEN_SESSION_REVOKED")
                        .queryParam("targetId", String.valueOf(session.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("TOKEN_SESSION_REVOKED"));
    }

    @Test
    void shouldAllowAdminToRevokeAllUserTokenSessions() throws Exception {
        String firstToken = objectMapper.readTree(loginResult("approver", "approver123").getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        String secondToken = objectMapper.readTree(loginResult("approver", "approver123").getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        assertThat(secondToken).isNotBlank();
        AuthTokenSession session = authTokenSessionRepository.findByUsernameOrderByIdDesc("approver").get(0);
        String adminToken = objectMapper.readTree(loginResult("admin", "admin123").getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(post("/api/v1/users/{userId}/token-sessions/revoke", session.getUserId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("approver"));

        assertThat(authTokenSessionRepository.findByUsernameOrderByIdDesc("approver"))
                .allMatch(tokenSession -> tokenSession.getRevokedAt() != null);

        mockMvc.perform(get("/api/v1/approvals/pending")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectNonAdminFromAdminOnlyApis() throws Exception {
        String userToken = objectMapper.readTree(loginResult("user", "user123").getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Forbidden KB",
                                  "description": "Should not be created"
                                }
                                """))
                .andExpect(status().isForbidden());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "forbidden.md",
                "text/markdown",
                "forbidden".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("knowledgeBaseId", "1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audits")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users/1/unlock")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users/token-sessions")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users/token-sessions/1/revoke")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users/1/token-sessions/revoke")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldEnforceFineGrainedPermissionsForApproverRole() throws Exception {
        String approverToken = objectMapper.readTree(loginResult("approver", "approver123").getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(get("/api/v1/approvals/pending")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": 1,
                                  "title": "Approver should not submit",
                                  "description": "Approver role can review approvals but cannot submit tickets.",
                                  "priority": "HIGH",
                                  "assigneeId": 3
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Approver Forbidden KB",
                                  "description": "Should not be created"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private MvcResult loginResult(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void insertTestUser(long id, String username, String passwordHash, int failedLoginCount, boolean locked) {
        jdbcTemplate.update("""
                        insert into app_user (
                            id, username, password_hash, display_name, status,
                            failed_login_count, locked_until, created_at, updated_at
                        ) values (?, ?, ?, ?, 'ACTIVE', ?, %s, current_timestamp, current_timestamp)
                        """.formatted(locked ? "dateadd('minute', 15, current_timestamp)" : "null"),
                id,
                username,
                passwordHash,
                "Test User",
                failedLoginCount
        );
    }

    private AuditLog latestAudit(String eventType, String targetType, Long targetId) {
        return auditLogRepository.findFirstByEventTypeAndTargetTypeAndTargetIdOrderByIdDesc(eventType, targetType, targetId)
                .orElse(null);
    }
}
