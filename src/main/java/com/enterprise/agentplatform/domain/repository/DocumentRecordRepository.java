package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface DocumentRecordRepository extends JpaRepository<DocumentRecord, Long> {

    long countByIndexStatus(ProcessingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentRecord> findLockedById(Long id);

    List<DocumentRecord> findByKnowledgeBaseIdOrderByIdDesc(Long knowledgeBaseId);

    List<DocumentRecord> findByKnowledgeBaseIdAndIndexStatusOrderByIdDesc(Long knowledgeBaseId, ProcessingStatus indexStatus);

    List<DocumentRecord> findByKnowledgeBaseIdAndFileNameContainingIgnoreCaseOrderByIdDesc(Long knowledgeBaseId, String keyword);

    List<DocumentRecord> findByKnowledgeBaseIdAndIndexStatusAndFileNameContainingIgnoreCaseOrderByIdDesc(
            Long knowledgeBaseId,
            ProcessingStatus indexStatus,
            String keyword
    );

    Page<DocumentRecord> findByKnowledgeBaseId(Long knowledgeBaseId, Pageable pageable);

    Page<DocumentRecord> findByKnowledgeBaseIdAndIndexStatus(Long knowledgeBaseId, ProcessingStatus indexStatus, Pageable pageable);

    Page<DocumentRecord> findByKnowledgeBaseIdAndFileNameContainingIgnoreCase(Long knowledgeBaseId, String keyword, Pageable pageable);

    Page<DocumentRecord> findByKnowledgeBaseIdAndIndexStatusAndFileNameContainingIgnoreCase(
            Long knowledgeBaseId,
            ProcessingStatus indexStatus,
            String keyword,
            Pageable pageable
    );
}
