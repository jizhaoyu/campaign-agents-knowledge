package com.enterprise.agentplatform.user.service;

import com.enterprise.agentplatform.audit.service.AuditService;
import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.api.PageResponse;
import com.enterprise.agentplatform.common.exception.BusinessException;
import com.enterprise.agentplatform.common.security.UserRoleResolver;
import com.enterprise.agentplatform.domain.entity.AuthTokenSession;
import com.enterprise.agentplatform.domain.entity.UserAccount;
import com.enterprise.agentplatform.domain.repository.AuthTokenSessionRepository;
import com.enterprise.agentplatform.domain.repository.UserAccountRepository;
import com.enterprise.agentplatform.user.dto.TokenSessionAdminResponse;
import com.enterprise.agentplatform.user.dto.UserAdminResponse;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleResolver userRoleResolver;
    private final AuthTokenSessionRepository authTokenSessionRepository;
    private final AuditService auditService;

    public UserAdminService(
            UserAccountRepository userAccountRepository,
            UserRoleResolver userRoleResolver,
            AuthTokenSessionRepository authTokenSessionRepository,
            AuditService auditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleResolver = userRoleResolver;
        this.authTokenSessionRepository = authTokenSessionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserAdminResponse> listUsers() {
        List<UserAccount> users = userAccountRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(UserAccount::getId))
                .toList();
        Map<Long, Set<String>> rolesByUserId = userRoleResolver.resolveRoleCodesByUserId(users.stream().map(UserAccount::getId).toList());
        return users.stream()
                .map(user -> toResponse(user, rolesByUserId.getOrDefault(user.getId(), Set.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<UserAdminResponse> listUsers(int page, int size) {
        Page<UserAccount> users = userAccountRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
        Map<Long, Set<String>> rolesByUserId = userRoleResolver.resolveRoleCodesByUserId(users.stream().map(UserAccount::getId).toList());
        return PageResponse.from(users.map(user -> toResponse(user, rolesByUserId.getOrDefault(user.getId(), Set.of()))));
    }

    @Transactional
    public UserAdminResponse unlockUser(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + userId));
        int previousFailedCount = user.getFailedLoginCount();
        boolean wasLocked = user.getLockedUntil() != null;
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        UserAccount saved = userAccountRepository.save(user);

        auditService.record("USER_UNLOCKED", "USER", saved.getId(), Map.of(
                "username", saved.getUsername(),
                "previousFailedLoginCount", previousFailedCount,
                "wasLocked", wasLocked
        ));
        return toResponse(saved, userRoleResolver.resolveRoleCodes(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<TokenSessionAdminResponse> listTokenSessions() {
        LocalDateTime now = LocalDateTime.now();
        return authTokenSessionRepository.findTop200ByOrderByIdDesc()
                .stream()
                .map(session -> toSessionResponse(session, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TokenSessionAdminResponse> listTokenSessions(int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        Page<AuthTokenSession> sessions = authTokenSessionRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return PageResponse.from(sessions.map(session -> toSessionResponse(session, now)));
    }

    @Transactional
    public TokenSessionAdminResponse revokeTokenSession(Long sessionId) {
        AuthTokenSession session = authTokenSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "token 会话不存在: " + sessionId));
        int revokedCount = authTokenSessionRepository.revokeById(sessionId, LocalDateTime.now());
        AuthTokenSession updatedSession = authTokenSessionRepository.findById(sessionId).orElseThrow();
        auditService.record("TOKEN_SESSION_REVOKED", "AUTH_TOKEN_SESSION", sessionId, Map.of(
                "username", session.getUsername(),
                "userId", session.getUserId(),
                "changed", revokedCount > 0
        ));
        return toSessionResponse(updatedSession, LocalDateTime.now());
    }

    @Transactional
    public List<TokenSessionAdminResponse> revokeUserTokenSessions(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + userId));
        int revokedCount = authTokenSessionRepository.revokeByUserId(userId, LocalDateTime.now());
        auditService.record("USER_TOKEN_SESSIONS_REVOKED", "USER", userId, Map.of(
                "username", user.getUsername(),
                "revokedCount", revokedCount
        ));
        LocalDateTime now = LocalDateTime.now();
        return authTokenSessionRepository.findByUsernameOrderByIdDesc(user.getUsername())
                .stream()
                .map(session -> toSessionResponse(session, now))
                .toList();
    }

    private UserAdminResponse toResponse(UserAccount user, Set<String> roles) {
        return new UserAdminResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus().name(),
                user.getFailedLoginCount(),
                user.getLockedUntil(),
                roles
        );
    }

    private TokenSessionAdminResponse toSessionResponse(AuthTokenSession session, LocalDateTime now) {
        return new TokenSessionAdminResponse(
                session.getId(),
                session.getUserId(),
                session.getUsername(),
                fingerprint(session.getTokenHash()),
                session.getRoleCodes(),
                session.getIssuedAt(),
                session.getExpiresAt(),
                session.getRefreshExpiresAt(),
                session.getLastRefreshedAt(),
                session.getRevokedAt(),
                session.getRevokedAt() == null && session.getExpiresAt().isAfter(now),
                session.getRevokedAt() == null && refreshExpiresAt(session).isAfter(now)
        );
    }

    private LocalDateTime refreshExpiresAt(AuthTokenSession session) {
        return session.getRefreshExpiresAt() == null ? session.getExpiresAt() : session.getRefreshExpiresAt();
    }

    private String fingerprint(String tokenHash) {
        if (tokenHash == null || tokenHash.length() <= 12) {
            return tokenHash;
        }
        return tokenHash.substring(0, 12);
    }
}
