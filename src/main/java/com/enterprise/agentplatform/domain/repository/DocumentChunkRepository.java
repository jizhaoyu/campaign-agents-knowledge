package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.DocumentChunk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByKnowledgeBaseId(Long knowledgeBaseId);

    long countByDocumentId(Long documentId);

    @Query("""
            select chunk.documentId as documentId, count(chunk.id) as chunkCount
            from DocumentChunk chunk
            where chunk.documentId in :documentIds
            group by chunk.documentId
            """)
    List<DocumentChunkCount> countChunksByDocumentIds(@Param("documentIds") Collection<Long> documentIds);

    void deleteByDocumentId(Long documentId);

    interface DocumentChunkCount {
        Long getDocumentId();

        Long getChunkCount();
    }
}
