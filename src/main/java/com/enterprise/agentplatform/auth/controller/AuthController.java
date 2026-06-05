package com.enterprise.agentplatform.auth.controller;

import com.enterprise.agentplatform.auth.dto.LoginRequest;
import com.enterprise.agentplatform.auth.dto.LoginResponse;
import com.enterprise.agentplatform.auth.dto.RefreshTokenRequest;
import com.enterprise.agentplatform.auth.service.AuthService;
import com.enterprise.agentplatform.common.api.ApiResponse;
import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.exception.BusinessException;
import com.enterprise.agentplatform.common.security.TokenPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        Object credentials = authentication == null ? null : authentication.getCredentials();
        if (!(credentials instanceof String token) || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 Bearer token");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof TokenPrincipal tokenPrincipal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或 token 无效");
        }
        authService.logout(token, tokenPrincipal);
        return ApiResponse.success(null);
    }
}
