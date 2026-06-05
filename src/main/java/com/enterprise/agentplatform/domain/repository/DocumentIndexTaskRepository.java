package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.DocumentIndexTask;
import com.enterprise.agentplatform.domain.enums.DocumentIndexTaskStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentIndexTaskRepository extends JpaRepository<DocumentIndexTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from DocumentIndexTask task
            where task.status = :status
              and task.nextRunAt <= :now
            order by task.id asc
            """)
    List<DocumentIndexTask> findDueTasks(
            @Param("status") DocumentIndexTaskStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from DocumentIndexTask task
            where task.status = :status
              and task.startedAt <= :staleBefore
            order by task.id asc
            """)
    List<DocumentIndexTask> findStaleRunningTasks(
            @Param("status") DocumentIndexTaskStatus status,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable
    );

    long countByStatus(DocumentIndexTaskStatus status);

    List<DocumentIndexTask> findByDocumentIdOrderByIdDesc(Long documentId);
}
