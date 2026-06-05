package com.enterprise.agentplatform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/actuator/health",
            "/error"
    );

    private final SimpleTokenStore tokenStore;
    private final RolePermissionMapper rolePermissionMapper;

    public TokenAuthenticationFilter(SimpleTokenStore tokenStore, RolePermissionMapper rolePermissionMapper) {
        this.tokenStore = tokenStore;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || PUBLIC_PATHS.stream().anyMatch(requestUri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            SecurityJsonWriter.writeUnauthorized(response, "UNAUTHORIZED", "缺少 Bearer token");
            return;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        Optional<TokenPrincipal> principalOptional = tokenStore.resolve(token);
        if (principalOptional.isEmpty()) {
            SecurityJsonWriter.writeUnauthorized(response, "UNAUTHORIZED", "token 无效或已过期");
            return;
        }

        TokenPrincipal principal = principalOptional.get();
        Set<SimpleGrantedAuthority> authorities = principal.roles()
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(java.util.stream.Collectors.toSet());
        rolePermissionMapper.permissionsFor(principal.roles())
                .stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                token,
                authorities
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
