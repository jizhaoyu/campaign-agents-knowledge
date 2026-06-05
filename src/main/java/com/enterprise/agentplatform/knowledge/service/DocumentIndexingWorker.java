package com.enterprise.agentplatform.knowledge.service;

import com.enterprise.agentplatform.audit.service.AuditService;
import com.enterprise.agentplatform.domain.entity.DocumentIndexTask;
import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.enums.DocumentIndexTaskStatus;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import com.enterprise.agentplatform.domain.repository.DocumentIndexTaskRepository;
import com.enterprise.agentplatform.domain.repository.DocumentRecordRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DocumentIndexingWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingWorker.class);

    private final DocumentIndexTaskRepository documentIndexTaskRepository;
    private final DocumentRecordRepository documentRecordRepository;
    private final DocumentIndexingService documentIndexingService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final Duration staleTimeout;

    public DocumentIndexingWorker(
            DocumentIndexTaskRepository documentIndexTaskRepository,
            DocumentRecordRepository documentRecordRepository,
            DocumentIndexingService documentIndexingService,
            AuditService auditService,
            TransactionTemplate transactionTemplate,
            @Value("${app.document-index.batch-size:5}") int batchSize,
            @Value("${app.document-index.stale-timeout-seconds:300}") long staleTimeoutSeconds
    ) {
        this.documentIndexTaskRepository = documentIndexTaskRepository;
        this.documentRecordRepository = documentRecordRepository;
        this.documentIndexingService = documentIndexingService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
        this.staleTimeout = Duration.ofSeconds(staleTimeoutSeconds);
    }

    @Async("documentIndexExecutor")
    public void triggerAsync() {
        drainDueTasks();
    }

    @Scheduled(fixedDelayString = "${app.document-index.poll-delay-ms:5000}")
    public void poll() {
        drainDueTasks();
    }

    void drainDueTasks() {
        recoverStaleRunningTasks();
        while (true) {
            List<Long> taskIds = claimDueTasks();
            if (taskIds.isEmpty()) {
                return;
            }
            for (Long taskId : taskIds) {
                processTask(taskId);
            }
        }
    }

    List<Long> claimDueTasks() {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            List<DocumentIndexTask> tasks = documentIndexTaskRepository.findDueTasks(
                    DocumentIndexTaskStatus.PENDING,
                    now,
                    PageRequest.of(0, batchSize)
            );
            for (DocumentIndexTask task : tasks) {
                task.setStatus(DocumentIndexTaskStatus.RUNNING);
                task.setStartedAt(now);
                task.setFinishedAt(null);
                task.setAttemptCount(task.getAttemptCount() + 1);
            }
            documentIndexTaskRepository.saveAll(tasks);
            return tasks.stream().map(DocumentIndexTask::getId).toList();
        });
    }

    void recoverStaleRunningTasks() {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime staleBefore = LocalDateTime.now().minus(staleTimeout);
            List<DocumentIndexTask> staleTasks = documentIndexTaskRepository.findStaleRunningTasks(
                    DocumentIndexTaskStatus.RUNNING,
                    staleBefore,
                    PageRequest.of(0, batchSize)
            );
            for (DocumentIndexTask task : staleTasks) {
                task.setStatus(DocumentIndexTaskStatus.PENDING);
                task.setNextRunAt(LocalDateTime.now());
                task.setLastError("索引任务执行超时，已重新入队");
            }
            documentIndexTaskRepository.saveAll(staleTasks);
        });
    }

    void processTask(Long taskId) {
        DocumentIndexTask task = documentIndexTaskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != DocumentIndexTaskStatus.RUNNING) {
            return;
        }
        try {
            DocumentIndexingResult result = documentIndexingService.index(task.getDocumentId(), task.getTraceId());
            if (result.completed()) {
                markTaskSuccess(taskId);
            } else {
                markTaskFailed(taskId, result.message());
            }
        } catch (RuntimeException ex) {
            log.warn("Document index task failed: taskId={}, documentId={}, attempt={}",
                    taskId, task.getDocumentId(), task.getAttemptCount(), ex);
            handleRetryableFailure(taskId, "文档索引失败");
        }
    }

    void markTaskSuccess(Long taskId) {
        transactionTemplate.executeWithoutResult(status -> {
            DocumentIndexTask task = documentIndexTaskRepository.findById(taskId).orElse(null);
            if (task == null) {
                return;
            }
            task.setStatus(DocumentIndexTaskStatus.SUCCESS);
            task.setLastError(null);
            task.setFinishedAt(LocalDateTime.now());
            documentIndexTaskRepository.save(task);
        });
    }

    void markTaskFailed(Long taskId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            DocumentIndexTask task = documentIndexTaskRepository.findById(taskId).orElse(null);
            if (task == null) {
                return;
            }
            task.setStatus(DocumentIndexTaskStatus.FAILED);
            task.setLastError(message);
            task.setFinishedAt(LocalDateTime.now());
            documentIndexTaskRepository.save(task);
        });
    }

    void handleRetryableFailure(Long taskId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            DocumentIndexTask task = documentIndexTaskRepository.findById(taskId).orElse(null);
            if (task == null) {
                return;
            }
            if (task.getAttemptCount() < task.getMaxAttempts()) {
                task.setStatus(DocumentIndexTaskStatus.PENDING);
                task.setLastError(message);
                task.setNextRunAt(LocalDateTime.now().plusSeconds(backoffSeconds(task.getAttemptCount())));
                task.setFinishedAt(null);
                markDocumentWaitingForRetry(task.getDocumentId(), message);
                documentIndexTaskRepository.save(task);
                auditService.record("DOCUMENT_INDEX_RETRY_SCHEDULED", "DOCUMENT", task.getDocumentId(), Map.of(
                        "taskId", task.getId(),
                        "attemptCount", task.getAttemptCount(),
                        "maxAttempts", task.getMaxAttempts(),
                        "message", message
                ));
                return;
            }
            task.setStatus(DocumentIndexTaskStatus.FAILED);
            task.setLastError(message);
            task.setFinishedAt(LocalDateTime.now());
            documentIndexTaskRepository.save(task);
            markDocumentFailed(task.getDocumentId(), message);
            auditService.record("DOCUMENT_INDEX_FAILED", "DOCUMENT", task.getDocumentId(), Map.of(
                    "taskId", task.getId(),
                    "attemptCount", task.getAttemptCount(),
                    "message", message
            ));
        });
    }

    private long backoffSeconds(int attemptCount) {
        return Math.min(60L, Math.max(1L, attemptCount) * 5L);
    }

    private void markDocumentWaitingForRetry(Long documentId, String message) {
        DocumentRecord record = documentRecordRepository.findById(documentId).orElse(null);
        if (record == null) {
            return;
        }
        record.setIndexStatus(ProcessingStatus.PENDING);
        record.setFailureReason(message + "，等待自动重试");
        documentRecordRepository.save(record);
    }

    private void markDocumentFailed(Long documentId, String message) {
        DocumentRecord record = documentRecordRepository.findById(documentId).orElse(null);
        if (record == null) {
            return;
        }
        if (record.getParseStatus() != ProcessingStatus.SUCCESS) {
            record.setParseStatus(ProcessingStatus.FAILED);
        }
        record.setIndexStatus(ProcessingStatus.FAILED);
        record.setFailureReason(message);
        documentRecordRepository.save(record);
    }
}
