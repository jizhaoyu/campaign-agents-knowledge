package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRecordRepository extends JpaRepository<DocumentRecord, Long> {

    long countByIndexStatus(ProcessingStatus status);

    List<DocumentRecord> findByKnowledgeBaseIdOrderByIdDesc(Long knowledgeBaseId);

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
