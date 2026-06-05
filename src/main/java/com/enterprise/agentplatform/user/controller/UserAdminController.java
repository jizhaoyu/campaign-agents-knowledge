package com.enterprise.agentplatform.user.controller;

import com.enterprise.agentplatform.common.api.ApiResponse;
import com.enterprise.agentplatform.common.api.PageRequestValidator;
import com.enterprise.agentplatform.user.dto.TokenSessionAdminResponse;
import com.enterprise.agentplatform.user.dto.UserAdminResponse;
import com.enterprise.agentplatform.user.service.UserAdminService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:admin')")
    public ApiResponse<?> listUsers(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        if (PageRequestValidator.isPaged(page, size)) {
            PageRequestValidator.Params pageRequest = PageRequestValidator.resolve(page, size, 20);
            return ApiResponse.success(userAdminService.listUsers(pageRequest.page(), pageRequest.size()));
        }
        return ApiResponse.success(userAdminService.listUsers());
    }

    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasAuthority('user:admin')")
    public ApiResponse<UserAdminResponse> unlockUser(@PathVariable Long userId) {
        return ApiResponse.success(userAdminService.unlockUser(userId));
    }

    @GetMapping("/token-sessions")
    @PreAuthorize("hasAuthority('token-session:admin')")
    public ApiResponse<?> listTokenSessions(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        if (PageRequestValidator.isPaged(page, size)) {
            PageRequestValidator.Params pageRequest = PageRequestValidator.resolve(page, size, 20);
            return ApiResponse.success(userAdminService.listTokenSessions(pageRequest.page(), pageRequest.size()));
        }
        return ApiResponse.success(userAdminService.listTokenSessions());
    }

    @PostMapping("/token-sessions/{sessionId}/revoke")
    @PreAuthorize("hasAuthority('token-session:admin')")
    public ApiResponse<TokenSessionAdminResponse> revokeTokenSession(@PathVariable Long sessionId) {
        return ApiResponse.success(userAdminService.revokeTokenSession(sessionId));
    }

    @PostMapping("/{userId}/token-sessions/revoke")
    @PreAuthorize("hasAuthority('token-session:admin')")
    public ApiResponse<List<TokenSessionAdminResponse>> revokeUserTokenSessions(@PathVariable Long userId) {
        return ApiResponse.success(userAdminService.revokeUserTokenSessions(userId));
    }
}
