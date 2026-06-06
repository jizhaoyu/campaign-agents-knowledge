package com.enterprise.agentplatform.knowledge.service;

import com.enterprise.agentplatform.audit.service.AuditService;
import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.api.PageResponse;
import com.enterprise.agentplatform.common.exception.BusinessException;
import com.enterprise.agentplatform.common.security.CurrentUserService;
import com.enterprise.agentplatform.common.support.TraceIdHolder;
import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import com.enterprise.agentplatform.domain.repository.DocumentChunkRepository;
import com.enterprise.agentplatform.domain.repository.DocumentRecordRepository;
import com.enterprise.agentplatform.knowledge.dto.DocumentUploadResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentRecordRepository documentRecordRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final CurrentUserService currentUserService;
    private final LocalFileStorageService localFileStorageService;
    private final DocumentIndexingDispatcher documentIndexingDispatcher;
    private final AuditService auditService;

    public DocumentService(
            KnowledgeBaseService knowledgeBaseService,
            DocumentRecordRepository documentRecordRepository,
            DocumentChunkRepository documentChunkRepository,
            CurrentUserService currentUserService,
            LocalFileStorageService localFileStorageService,
            DocumentIndexingDispatcher documentIndexingDispatcher,
            AuditService auditService
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentRecordRepository = documentRecordRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.currentUserService = currentUserService;
        this.localFileStorageService = localFileStorageService;
        this.documentIndexingDispatcher = documentIndexingDispatcher;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentUploadResponse upload(Long knowledgeBaseId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        knowledgeBaseService.requireExisting(knowledgeBaseId);

        String objectKey = localFileStorageService.save(file);
        DocumentRecord record = new DocumentRecord();
        record.setKnowledgeBaseId(knowledgeBaseId);
        record.setFileName(file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename());
        record.setFileType(resolveFileType(record.getFileName()));
        record.setObjectKey(objectKey);
        record.setParseStatus(ProcessingStatus.PENDING);
        record.setIndexStatus(ProcessingStatus.PENDING);
        record.setUploadedBy(currentUserService.requireUserId());
        record = documentRecordRepository.save(record);

        auditService.record("DOCUMENT_UPLOADED", "DOCUMENT", record.getId(), java.util.Map.of(
                "fileName", record.getFileName(),
                "knowledgeBaseId", knowledgeBaseId
        ));

        documentIndexingDispatcher.dispatchAfterCommit(record.getId(), TraceIdHolder.currentTraceId());
        return toResponse(record);
    }

    public DocumentUploadResponse getStatus(Long documentId) {
        DocumentRecord record = documentRecordRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在: " + documentId));
        return toResponse(record);
    }

    public List<DocumentUploadResponse> listByKnowledgeBase(Long knowledgeBaseId) {
        return listByKnowledgeBase(knowledgeBaseId, null, null);
    }

    public List<DocumentUploadResponse> listByKnowledgeBase(Long knowledgeBaseId, String keyword, String indexStatus) {
        knowledgeBaseService.requireExisting(knowledgeBaseId);
        ProcessingStatus statusFilter = parseStatus(indexStatus);
        String keywordFilter = normalizeKeyword(keyword);
        return toResponses(findDocumentRecords(knowledgeBaseId, keywordFilter, statusFilter));
    }

    public PageResponse<DocumentUploadResponse> listByKnowledgeBase(
            Long knowledgeBaseId,
            String keyword,
            String indexStatus,
            int page,
            int size
    ) {
        knowledgeBaseService.requireExisting(knowledgeBaseId);
        ProcessingStatus statusFilter = parseStatus(indexStatus);
        String keywordFilter = normalizeKeyword(keyword);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<DocumentRecord> records = findDocumentPage(knowledgeBaseId, keywordFilter, statusFilter, pageRequest);
        if (records.isEmpty()) {
            return PageResponse.from(records.map(record -> toResponse(record, 0L)));
        }
        Map<Long, Long> chunkCountByDocumentId = chunkCountByDocumentId(records.stream().map(DocumentRecord::getId).toList());
        return PageResponse.from(records.map(record -> toResponse(record, chunkCountByDocumentId.getOrDefault(record.getId(), 0L))));
    }

    @Transactional
    public DocumentUploadResponse reindex(Long documentId) {
        DocumentRecord record = documentRecordRepository.findLockedById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在: " + documentId));
        return queueReindex(record);
    }

    @Transactional
    public List<DocumentUploadResponse> retryFailed(Long knowledgeBaseId) {
        knowledgeBaseService.requireExisting(knowledgeBaseId);
        List<DocumentRecord> failedRecords = documentRecordRepository.findByKnowledgeBaseIdOrderByIdDesc(knowledgeBaseId)
                .stream()
                .filter(record -> record.getIndexStatus() == ProcessingStatus.FAILED)
                .toList();
        List<DocumentUploadResponse> responses = new ArrayList<>();
        for (DocumentRecord record : failedRecords) {
            documentRecordRepository.findLockedById(record.getId())
                    .filter(lockedRecord -> lockedRecord.getIndexStatus() == ProcessingStatus.FAILED)
                    .map(this::queueReindex)
                    .ifPresent(responses::add);
        }
        return responses;
    }

    @Transactional
    public void delete(Long documentId) {
        DocumentRecord record = documentRecordRepository.findLockedById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在: " + documentId));
        if (record.getIndexStatus() == ProcessingStatus.PENDING) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文档正在索引中，暂不能删除");
        }
        documentChunkRepository.deleteByDocumentId(record.getId());
        documentRecordRepository.delete(record);
        auditService.record("DOCUMENT_DELETED", "DOCUMENT", record.getId(), java.util.Map.of(
                "fileName", record.getFileName(),
                "knowledgeBaseId", record.getKnowledgeBaseId()
        ));
        deleteStoredFileAfterCommit(record.getObjectKey());
    }

    private DocumentUploadResponse queueReindex(DocumentRecord record) {
        if (record.getIndexStatus() == ProcessingStatus.PENDING) {
            return toResponse(record);
        }
        record.setParseStatus(ProcessingStatus.PENDING);
        record.setIndexStatus(ProcessingStatus.PENDING);
        record.setFailureReason(null);
        record = documentRecordRepository.save(record);

        auditService.record("DOCUMENT_REINDEX_REQUESTED", "DOCUMENT", record.getId(), java.util.Map.of(
                "fileName", record.getFileName(),
                "knowledgeBaseId", record.getKnowledgeBaseId()
        ));

        documentIndexingDispatcher.dispatchAfterCommit(record.getId(), TraceIdHolder.currentTraceId());
        return toResponse(record);
    }

    private void deleteStoredFileAfterCommit(String objectKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    localFileStorageService.deleteIfExists(objectKey);
                }
            });
            return;
        }
        localFileStorageService.deleteIfExists(objectKey);
    }

    private DocumentUploadResponse toResponse(DocumentRecord record) {
        return toResponse(record, documentChunkRepository.countByDocumentId(record.getId()));
    }

    private DocumentUploadResponse toResponse(DocumentRecord record, long chunkCount) {
        return new DocumentUploadResponse(
                record.getId(),
                record.getFileName(),
                record.getParseStatus().name(),
                record.getIndexStatus().name(),
                chunkCount,
                record.getFailureReason()
        );
    }

    private List<DocumentUploadResponse> toResponses(List<DocumentRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> chunkCountByDocumentId = chunkCountByDocumentId(records.stream().map(DocumentRecord::getId).toList());
        return records.stream()
                .map(record -> toResponse(record, chunkCountByDocumentId.getOrDefault(record.getId(), 0L)))
                .toList();
    }

    private Map<Long, Long> chunkCountByDocumentId(List<Long> documentIds) {
        return documentChunkRepository.countChunksByDocumentIds(documentIds)
                .stream()
                .collect(Collectors.toMap(
                        DocumentChunkRepository.DocumentChunkCount::getDocumentId,
                        DocumentChunkRepository.DocumentChunkCount::getChunkCount
                ));
    }

    private ProcessingStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ProcessingStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的索引状态: " + value);
        }
    }

    private String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Page<DocumentRecord> findDocumentPage(
            Long knowledgeBaseId,
            String keywordFilter,
            ProcessingStatus statusFilter,
            PageRequest pageRequest
    ) {
        if (keywordFilter != null && statusFilter != null) {
            return documentRecordRepository.findByKnowledgeBaseIdAndIndexStatusAndFileNameContainingIgnoreCase(
                    knowledgeBaseId,
                    statusFilter,
                    keywordFilter,
                    pageRequest
            );
        }
        if (keywordFilter != null) {
            return documentRecordRepository.findByKnowledgeBaseIdAndFileNameContainingIgnoreCase(
                    knowledgeBaseId,
                    keywordFilter,
                    pageRequest
            );
        }
        if (statusFilter != null) {
            return documentRecordRepository.findByKnowledgeBaseIdAndIndexStatus(
                    knowledgeBaseId,
                    statusFilter,
                    pageRequest
            );
        }
        return documentRecordRepository.findByKnowledgeBaseId(knowledgeBaseId, pageRequest);
    }

    private List<DocumentRecord> findDocumentRecords(
            Long knowledgeBaseId,
            String keywordFilter,
            ProcessingStatus statusFilter
    ) {
        if (keywordFilter != null && statusFilter != null) {
            return documentRecordRepository.findByKnowledgeBaseIdAndIndexStatusAndFileNameContainingIgnoreCaseOrderByIdDesc(
                    knowledgeBaseId,
                    statusFilter,
                    keywordFilter
            );
        }
        if (keywordFilter != null) {
            return documentRecordRepository.findByKnowledgeBaseIdAndFileNameContainingIgnoreCaseOrderByIdDesc(
                    knowledgeBaseId,
                    keywordFilter
            );
        }
        if (statusFilter != null) {
            return documentRecordRepository.findByKnowledgeBaseIdAndIndexStatusOrderByIdDesc(
                    knowledgeBaseId,
                    statusFilter
            );
        }
        return documentRecordRepository.findByKnowledgeBaseIdOrderByIdDesc(knowledgeBaseId);
    }

    private String resolveFileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return "UNKNOWN";
        }
        return fileName.substring(index + 1).toUpperCase(java.util.Locale.ROOT);
    }
}
