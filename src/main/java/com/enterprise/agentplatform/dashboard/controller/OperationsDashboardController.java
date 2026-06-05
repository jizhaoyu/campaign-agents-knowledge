package com.enterprise.agentplatform.dashboard.controller;

import com.enterprise.agentplatform.common.api.ApiResponse;
import com.enterprise.agentplatform.dashboard.dto.OperationsDashboardResponse;
import com.enterprise.agentplatform.dashboard.service.OperationsDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class OperationsDashboardController {

    private final OperationsDashboardService operationsDashboardService;

    public OperationsDashboardController(OperationsDashboardService operationsDashboardService) {
        this.operationsDashboardService = operationsDashboardService;
    }

    @GetMapping("/operations")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ApiResponse<OperationsDashboardResponse> operations() {
        return ApiResponse.success(operationsDashboardService.summarize());
    }
}
