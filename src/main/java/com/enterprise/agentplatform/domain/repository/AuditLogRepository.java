package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.AuditLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop200ByOrderByIdDesc();

    @Query("""
            select log
            from AuditLog log
            where (:traceId is null or log.traceId = :traceId)
              and (:eventType is null or log.eventType = :eventType)
              and (:targetType is null or log.targetType = :targetType)
              and (:targetId is null or log.targetId = :targetId)
            """)
    Page<AuditLog> search(
            @Param("traceId") String traceId,
            @Param("eventType") String eventType,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            Pageable pageable
    );

    Optional<AuditLog> findFirstByEventTypeAndTargetTypeAndTargetIdOrderByIdDesc(
            String eventType,
            String targetType,
            Long targetId
    );
}
