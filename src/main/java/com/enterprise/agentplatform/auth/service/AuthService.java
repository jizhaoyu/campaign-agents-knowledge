package com.enterprise.agentplatform.auth.service;

import com.enterprise.agentplatform.audit.service.AuditService;
import com.enterprise.agentplatform.auth.dto.LoginRequest;
import com.enterprise.agentplatform.auth.dto.LoginResponse;
import com.enterprise.agentplatform.auth.dto.RefreshTokenRequest;
import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.exception.BusinessException;
import com.enterprise.agentplatform.common.security.SimpleTokenStore;
import com.enterprise.agentplatform.common.security.SimpleTokenStore.TokenPair;
import com.enterprise.agentplatform.common.security.RolePermissionMapper;
import com.enterprise.agentplatform.common.security.TokenPrincipal;
import com.enterprise.agentplatform.domain.entity.AuthTokenSession;
import com.enterprise.agentplatform.domain.entity.Role;
import com.enterprise.agentplatform.domain.entity.UserAccount;
import com.enterprise.agentplatform.domain.entity.UserRole;
import com.enterprise.agentplatform.domain.enums.UserStatus;
import com.enterprise.agentplatform.domain.repository.RoleRepository;
import com.enterprise.agentplatform.domain.repository.UserAccountRepository;
import com.enterprise.agentplatform.domain.repository.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SimpleTokenStore tokenStore;
    private final RolePermissionMapper rolePermissionMapper;
    private final AuditService auditService;
    private final int maxFailedLoginAttempts;
    private final long loginLockoutMinutes;

    public AuthService(
            UserAccountRepository userAccountRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            SimpleTokenStore tokenStore,
            RolePermissionMapper rolePermissionMapper,
            AuditService auditService,
            @Value("${app.security.max-failed-login-attempts:5}") int maxFailedLoginAttempts,
            @Value("${app.security.login-lockout-minutes:15}") long loginLockoutMinutes
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.rolePermissionMapper = rolePermissionMapper;
        this.auditService = auditService;
        this.maxFailedLoginAttempts = Math.max(1, maxFailedLoginAttempts);
        this.loginLockoutMinutes = Math.max(1L, loginLockoutMinutes);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> {
                    auditLoginFailed(null, username, "USER_NOT_FOUND", 0, false);
                    return new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
                });
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditLoginFailed(user.getId(), user.getUsername(), "ACCOUNT_DISABLED", user.getFailedLoginCount(), false);
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号不可用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            auditLoginLocked(user, "LOCK_STILL_ACTIVE");
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "登录失败次数过多，请稍后再试");
        }
        clearExpiredLock(user, now);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean locked = recordFailedLogin(user, now);
            auditLoginFailed(user.getId(), user.getUsername(), "BAD_CREDENTIALS", user.getFailedLoginCount(), locked);
            if (locked) {
                auditLoginLocked(user, "MAX_FAILED_ATTEMPTS");
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        resetFailedLogin(user);

        Set<String> roles = resolveRoleCodes(user.getId());

        Set<String> permissions = rolePermissionMapper.permissionsFor(roles);

        TokenPair tokenPair = tokenStore.issue(new TokenPrincipal(user.getId(), user.getUsername(), roles, permissions));
        auditService.recordForActor(user.getId(), "USER_LOGIN_SUCCEEDED", "USER", user.getId(), Map.of(
                "username", user.getUsername(),
                "roles", roles,
                "permissions", permissions
        ));
        return toLoginResponse(tokenPair, user, roles, permissions);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse refresh(RefreshTokenRequest request) {
        AuthTokenSession session = tokenStore.findByRefreshToken(request.refreshToken())
                .orElseThrow(() -> {
                    auditService.recordForActor(null, "USER_TOKEN_REFRESH_FAILED", "AUTH_TOKEN_SESSION", null, Map.of(
                            "reason", "REFRESH_TOKEN_NOT_FOUND"
                    ));
                    return new BusinessException(ErrorCode.UNAUTHORIZED, "refresh token 无效或已过期");
                });
        UserAccount user = userAccountRepository.findById(session.getUserId())
                .orElseThrow(() -> {
                    auditTokenRefreshFailed(session, "USER_NOT_FOUND");
                    return new BusinessException(ErrorCode.UNAUTHORIZED, "refresh token 无效或已过期");
                });
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditTokenRefreshFailed(session, "ACCOUNT_DISABLED");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号不可用");
        }

        Set<String> roles = resolveRoleCodes(user.getId());
        Set<String> permissions = rolePermissionMapper.permissionsFor(roles);
        SimpleTokenStore.RefreshedTokenSession refreshedSession = tokenStore.refresh(
                        request.refreshToken(),
                        new TokenPrincipal(user.getId(), user.getUsername(), roles, permissions)
                )
                .orElseThrow(() -> {
                    auditTokenRefreshFailed(session, "REFRESH_TOKEN_EXPIRED_OR_REVOKED");
                    return new BusinessException(ErrorCode.UNAUTHORIZED, "refresh token 无效或已过期");
                });
        auditService.recordForActor(user.getId(), "USER_TOKEN_REFRESHED", "AUTH_TOKEN_SESSION", refreshedSession.session().getId(), Map.of(
                "username", user.getUsername()
        ));
        return toLoginResponse(refreshedSession.tokenPair(), user, roles, permissions);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void logout(String token, TokenPrincipal principal) {
        if (principal == null) {
            auditService.recordForActor(null, "USER_LOGOUT_FAILED", "AUTH_TOKEN", null, Map.of("reason", "PRINCIPAL_MISSING"));
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或 token 无效");
        }
        if (!tokenStore.revoke(token)) {
            auditService.recordForActor(
                    principal.userId(),
                    "USER_LOGOUT_FAILED",
                    "USER",
                    principal.userId(),
                    Map.of("reason", "TOKEN_INVALID_OR_EXPIRED")
            );
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "token 无效或已过期");
        }
        auditService.recordForActor(principal.userId(), "USER_LOGOUT", "USER", principal.userId(), Map.of(
                "username", principal.username()
        ));
    }

    private boolean recordFailedLogin(UserAccount user, LocalDateTime now) {
        int nextFailedCount = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(nextFailedCount);
        boolean locked = false;
        if (nextFailedCount >= maxFailedLoginAttempts) {
            user.setLockedUntil(now.plusMinutes(loginLockoutMinutes));
            locked = true;
        }
        userAccountRepository.save(user);
        return locked;
    }

    private void resetFailedLogin(UserAccount user) {
        if (user.getFailedLoginCount() == 0 && user.getLockedUntil() == null) {
            return;
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userAccountRepository.save(user);
    }

    private Set<String> resolveRoleCodes(Long userId) {
        return userRoleRepository.findByUserId(userId)
                .stream()
                .map(UserRole::getRoleId)
                .map(roleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(Role::getCode)
                .collect(Collectors.toSet());
    }

    private LoginResponse toLoginResponse(TokenPair tokenPair, UserAccount user, Set<String> roles, Set<String> permissions) {
        return new LoginResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.expiresIn(),
                tokenPair.refreshExpiresIn(),
                user.getUsername(),
                user.getDisplayName(),
                roles,
                permissions
        );
    }

    private void clearExpiredLock(UserAccount user, LocalDateTime now) {
        if (user.getLockedUntil() == null || user.getLockedUntil().isAfter(now)) {
            return;
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userAccountRepository.save(user);
    }

    private void auditLoginFailed(Long userId, String username, String reason, int failedLoginCount, boolean locked) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("reason", reason);
        payload.put("failedLoginCount", failedLoginCount);
        payload.put("locked", locked);
        auditService.recordForActor(null, "USER_LOGIN_FAILED", "USER", userId, payload);
    }

    private void auditLoginLocked(UserAccount user, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", user.getUsername());
        payload.put("reason", reason);
        payload.put("failedLoginCount", user.getFailedLoginCount());
        payload.put("lockedUntil", user.getLockedUntil());
        auditService.recordForActor(null, "USER_LOGIN_LOCKED", "USER", user.getId(), payload);
    }

    private void auditTokenRefreshFailed(AuthTokenSession session, String reason) {
        auditService.recordForActor(session.getUserId(), "USER_TOKEN_REFRESH_FAILED", "AUTH_TOKEN_SESSION", session.getId(), Map.of(
                "username", session.getUsername(),
                "reason", reason
        ));
    }
}
