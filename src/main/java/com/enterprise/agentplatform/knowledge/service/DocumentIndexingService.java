package com.enterprise.agentplatform.knowledge.service;

import com.enterprise.agentplatform.ai.service.EmbeddingService;
import com.enterprise.agentplatform.audit.service.AuditService;
import com.enterprise.agentplatform.common.exception.BusinessException;
import com.enterprise.agentplatform.common.support.TraceIdHolder;
import com.enterprise.agentplatform.domain.entity.DocumentChunk;
import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import com.enterprise.agentplatform.domain.repository.DocumentChunkRepository;
import com.enterprise.agentplatform.domain.repository.DocumentRecordRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIndexingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);

    private final DocumentRecordRepository documentRecordRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final LocalFileStorageService localFileStorageService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final AuditService auditService;

    public DocumentIndexingService(
            DocumentRecordRepository documentRecordRepository,
            DocumentChunkRepository documentChunkRepository,
            LocalFileStorageService localFileStorageService,
            DocumentTextExtractor documentTextExtractor,
            ChunkingService chunkingService,
            EmbeddingService embeddingService,
            AuditService auditService
    ) {
        this.documentRecordRepository = documentRecordRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.localFileStorageService = localFileStorageService;
        this.documentTextExtractor = documentTextExtractor;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentIndexingResult index(Long documentId, String traceId) {
        TraceIdHolder.bind(traceId);
        try {
            return indexDocument(documentId);
        } finally {
            TraceIdHolder.clear();
        }
    }

    private DocumentIndexingResult indexDocument(Long documentId) {
        DocumentRecord record = documentRecordRepository.findById(documentId).orElse(null);
        if (record == null) {
            log.warn("Skip document indexing because record is missing: documentId={}", documentId);
            return DocumentIndexingResult.permanentFailure("文档不存在");
        }

        try {
            Path path = localFileStorageService.resolve(record.getObjectKey());
            String text = documentTextExtractor.extract(path, record.getFileName());
            List<DocumentChunk> chunks = buildChunks(record, text);
            persistSuccess(record, chunks);
            return DocumentIndexingResult.success();
        } catch (BusinessException ex) {
            log.warn("Document indexing failed with business error: documentId={}, reason={}", documentId, ex.getMessage());
            markFailed(record, ex.getMessage());
            return DocumentIndexingResult.permanentFailure(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Document indexing failed unexpectedly: documentId={}", documentId, ex);
            throw ex;
        }
    }

    private List<DocumentChunk> buildChunks(DocumentRecord record, String text) {
        List<ChunkingService.ChunkSlice> slices = chunkingService.chunk(text);
        List<DocumentChunk> chunks = new ArrayList<>();
        for (ChunkingService.ChunkSlice slice : slices) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(record.getId());
            chunk.setKnowledgeBaseId(record.getKnowledgeBaseId());
            chunk.setChunkNo(slice.chunkNo());
            chunk.setContent(slice.content());
            chunk.setEmbeddingJson(embeddingService.embedAsJson(slice.content()).orElse(null));
            chunk.setTokenCount(slice.tokenCount());
            chunk.setStartOffset(slice.startOffset());
            chunk.setEndOffset(slice.endOffset());
            chunks.add(chunk);
        }
        return chunks;
    }

    private void persistSuccess(DocumentRecord record, List<DocumentChunk> chunks) {
        record.setParseStatus(ProcessingStatus.SUCCESS);
        record.setFailureReason(null);
        documentChunkRepository.deleteByDocumentId(record.getId());
        documentChunkRepository.saveAll(chunks);
        record.setIndexStatus(ProcessingStatus.SUCCESS);
        documentRecordRepository.save(record);
        auditService.record("DOCUMENT_INDEXED", "DOCUMENT", record.getId(), Map.of("chunkCount", chunks.size()));
    }

    private void markFailed(DocumentRecord record, String reason) {
        if (record.getParseStatus() != ProcessingStatus.SUCCESS) {
            record.setParseStatus(ProcessingStatus.FAILED);
        }
        record.setIndexStatus(ProcessingStatus.FAILED);
        record.setFailureReason(reason);
        documentRecordRepository.save(record);
        auditService.record("DOCUMENT_INDEX_FAILED", "DOCUMENT", record.getId(), Map.of("message", reason));
    }
}
