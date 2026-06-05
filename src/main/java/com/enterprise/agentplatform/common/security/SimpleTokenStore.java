package com.enterprise.agentplatform.common.security;

import com.enterprise.agentplatform.domain.entity.AuthTokenSession;
import com.enterprise.agentplatform.domain.repository.AuthTokenSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SimpleTokenStore {

    private final AuthTokenSessionRepository authTokenSessionRepository;
    private final RolePermissionMapper rolePermissionMapper;
    private final long tokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public SimpleTokenStore(
            AuthTokenSessionRepository authTokenSessionRepository,
            RolePermissionMapper rolePermissionMapper,
            @Value("${app.security.token-ttl-seconds:7200}") long tokenTtlSeconds,
            @Value("${app.security.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds
    ) {
        this.authTokenSessionRepository = authTokenSessionRepository;
        this.rolePermissionMapper = rolePermissionMapper;
        this.tokenTtlSeconds = Math.max(0L, tokenTtlSeconds);
        this.refreshTokenTtlSeconds = Math.max(0L, refreshTokenTtlSeconds);
    }

    @Transactional
    public TokenPair issue(TokenPrincipal principal) {
        String accessToken = randomToken();
        String refreshToken = randomToken();
        LocalDateTime now = LocalDateTime.now();
        AuthTokenSession session = new AuthTokenSession();
        session.setTokenHash(hashToken(accessToken));
        session.setRefreshTokenHash(hashToken(refreshToken));
        session.setUserId(principal.userId());
        session.setUsername(principal.username());
        session.setRoleCodes(toRoleCodes(principal.roles()));
        session.setIssuedAt(now);
        session.setExpiresAt(now.plusSeconds(tokenTtlSeconds));
        session.setRefreshExpiresAt(now.plusSeconds(refreshTokenTtlSeconds));
        session.setLastRefreshedAt(null);
        session.setRevokedAt(null);
        authTokenSessionRepository.save(session);
        return new TokenPair(accessToken, refreshToken, tokenTtlSeconds, refreshTokenTtlSeconds);
    }

    @Transactional
    public Optional<TokenPrincipal> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthTokenSession> sessionOptional = authTokenSessionRepository.findByTokenHash(hashToken(token.trim()));
        if (sessionOptional.isEmpty()) {
            return Optional.empty();
        }
        AuthTokenSession session = sessionOptional.get();
        LocalDateTime now = LocalDateTime.now();
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        Set<String> roles = parseRoleCodes(session.getRoleCodes());
        return Optional.of(new TokenPrincipal(
                session.getUserId(),
                session.getUsername(),
                roles,
                rolePermissionMapper.permissionsFor(roles)
        ));
    }

    @Transactional
    public Optional<RefreshedTokenSession> refresh(String refreshToken, TokenPrincipal principal) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthTokenSession> sessionOptional = authTokenSessionRepository.findByRefreshTokenHash(hashToken(refreshToken.trim()));
        if (sessionOptional.isEmpty()) {
            return Optional.empty();
        }
        AuthTokenSession session = sessionOptional.get();
        LocalDateTime now = LocalDateTime.now();
        if (session.getRevokedAt() != null || session.getRefreshExpiresAt() == null || !session.getRefreshExpiresAt().isAfter(now)) {
            revoke(session, now);
            return Optional.empty();
        }

        String accessToken = randomToken();
        String nextRefreshToken = randomToken();
        session.setTokenHash(hashToken(accessToken));
        session.setRefreshTokenHash(hashToken(nextRefreshToken));
        session.setUserId(principal.userId());
        session.setUsername(principal.username());
        session.setRoleCodes(toRoleCodes(principal.roles()));
        session.setExpiresAt(now.plusSeconds(tokenTtlSeconds));
        session.setRefreshExpiresAt(now.plusSeconds(refreshTokenTtlSeconds));
        session.setLastRefreshedAt(now);
        authTokenSessionRepository.save(session);
        TokenPair tokenPair = new TokenPair(accessToken, nextRefreshToken, tokenTtlSeconds, refreshTokenTtlSeconds);
        return Optional.of(new RefreshedTokenSession(session, tokenPair));
    }

    @Transactional(readOnly = true)
    public Optional<AuthTokenSession> findByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return authTokenSessionRepository.findByRefreshTokenHash(hashToken(refreshToken.trim()));
    }

    public long tokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public long refreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    @Transactional
    public boolean revoke(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return authTokenSessionRepository.revokeByTokenHash(hashToken(token.trim()), LocalDateTime.now()) > 0;
    }

    @Transactional
    public int revokeExpiredSessions() {
        return authTokenSessionRepository.revokeExpiredSessions(LocalDateTime.now());
    }

    private void revoke(AuthTokenSession session, LocalDateTime now) {
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(now);
            authTokenSessionRepository.save(session);
        }
    }

    private String randomToken() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    private String toRoleCodes(Set<String> roles) {
        return roles.stream()
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private Set<String> parseRoleCodes(String roleCodes) {
        if (roleCodes == null || roleCodes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(roleCodes.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn) {
    }

    public record RefreshedTokenSession(AuthTokenSession session, TokenPair tokenPair) {
    }
}
