package com.enterprise.agentplatform.ai.controller;

import com.enterprise.agentplatform.ai.dto.AiRuntimeStatusResponse;
import com.enterprise.agentplatform.ai.service.AiRuntimeStatusService;
import com.enterprise.agentplatform.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiRuntimeController {

    private final AiRuntimeStatusService aiRuntimeStatusService;

    public AiRuntimeController(AiRuntimeStatusService aiRuntimeStatusService) {
        this.aiRuntimeStatusService = aiRuntimeStatusService;
    }

    @GetMapping("/runtime")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ApiResponse<AiRuntimeStatusResponse> runtime() {
        return ApiResponse.success(aiRuntimeStatusService.describe());
    }
}
