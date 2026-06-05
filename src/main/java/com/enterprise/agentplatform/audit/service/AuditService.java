package com.enterprise.agentplatform.audit.service;

import com.enterprise.agentplatform.audit.dto.AuditLogResponse;
import com.enterprise.agentplatform.common.api.PageResponse;
import com.enterprise.agentplatform.common.security.CurrentUserService;
import com.enterprise.agentplatform.common.support.TraceIdHolder;
import com.enterprise.agentplatform.domain.entity.AuditLog;
import com.enterprise.agentplatform.domain.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;

    public AuditService(
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper,
            CurrentUserService currentUserService
    ) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void record(String eventType, String targetType, Long targetId, Object payload) {
        save(resolveCurrentActorId(), eventType, targetType, targetId, payload);
    }

    @Transactional
    public void recordForActor(Long actorId, String eventType, String targetType, Long targetId, Object payload) {
        save(actorId, eventType, targetType, targetId, payload);
    }

    private void save(Long actorId, String eventType, String targetType, Long targetId, Object payload) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setEventType(eventType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTraceId(TraceIdHolder.currentTraceId());
        log.setPayloadJson(toJson(payload));
        auditLogRepository.save(log);
    }

    public List<AuditLogResponse> list(String traceId, String eventType, String targetType, Long targetId) {
        return auditLogRepository.findTop200ByOrderByIdDesc()
                .stream()
                .filter(log -> traceId == null || Objects.equals(traceId, log.getTraceId()))
                .filter(log -> eventType == null || Objects.equals(eventType, log.getEventType()))
                .filter(log -> targetType == null || Objects.equals(targetType, log.getTargetType()))
                .filter(log -> targetId == null || Objects.equals(targetId, log.getTargetId()))
                .map(log -> new AuditLogResponse(
                        log.getId(),
                        log.getActorId(),
                        log.getEventType(),
                        log.getTargetType(),
                        log.getTargetId(),
                        log.getTraceId(),
                        log.getPayloadJson()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(String traceId, String eventType, String targetType, Long targetId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.from(auditLogRepository.search(
                blankToNull(traceId),
                blankToNull(eventType),
                blankToNull(targetType),
                targetId,
                pageRequest
        ).map(this::toResponse));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getEventType(),
                log.getTargetType(),
                log.getTargetId(),
                log.getTraceId(),
                log.getPayloadJson()
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long resolveCurrentActorId() {
        try {
            return currentUserService.requireUserId();
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"serialization\":\"failed\"}";
        }
    }
}
