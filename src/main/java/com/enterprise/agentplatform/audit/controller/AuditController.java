package com.enterprise.agentplatform.audit.controller;

import com.enterprise.agentplatform.audit.dto.AuditLogResponse;
import com.enterprise.agentplatform.audit.service.AuditService;
import com.enterprise.agentplatform.common.api.ApiResponse;
import com.enterprise.agentplatform.common.api.PageRequestValidator;
import com.enterprise.agentplatform.common.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<?> list(
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) Long targetId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        if (PageRequestValidator.isPaged(page, size)) {
            PageRequestValidator.Params pageRequest = PageRequestValidator.resolve(page, size, 20);
            PageResponse<AuditLogResponse> audits = auditService.list(
                    traceId,
                    eventType,
                    targetType,
                    targetId,
                    pageRequest.page(),
                    pageRequest.size()
            );
            return ApiResponse.success(audits);
        }
        return ApiResponse.success(auditService.list(traceId, eventType, targetType, targetId));
    }
}
