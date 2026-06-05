package com.enterprise.agentplatform.knowledge.service;

import com.enterprise.agentplatform.domain.entity.DocumentIndexTask;
import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.enums.DocumentIndexTaskStatus;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import com.enterprise.agentplatform.domain.repository.DocumentIndexTaskRepository;
import com.enterprise.agentplatform.domain.repository.DocumentRecordRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DocumentIndexingDispatcher {

    private final DocumentRecordRepository documentRecordRepository;
    private final DocumentIndexTaskRepository documentIndexTaskRepository;
    private final DocumentIndexingWorker documentIndexingWorker;
    private final int maxAttempts;

    public DocumentIndexingDispatcher(
            DocumentRecordRepository documentRecordRepository,
            DocumentIndexTaskRepository documentIndexTaskRepository,
            DocumentIndexingWorker documentIndexingWorker,
            @Value("${app.document-index.max-attempts:3}") int maxAttempts
    ) {
        this.documentRecordRepository = documentRecordRepository;
        this.documentIndexTaskRepository = documentIndexTaskRepository;
        this.documentIndexingWorker = documentIndexingWorker;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void dispatchAfterCommit(Long documentId, String traceId) {
        enqueue(documentId, traceId);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            documentIndexingWorker.triggerAsync();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentIndexingWorker.triggerAsync();
            }
        });
    }

    private void enqueue(Long documentId, String traceId) {
        DocumentRecord record = documentRecordRepository.findById(documentId).orElse(null);
        if (record != null) {
            record.setParseStatus(ProcessingStatus.PENDING);
            record.setIndexStatus(ProcessingStatus.PENDING);
            record.setFailureReason(null);
            documentRecordRepository.save(record);
        }

        DocumentIndexTask task = new DocumentIndexTask();
        task.setDocumentId(documentId);
        task.setStatus(DocumentIndexTaskStatus.PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(maxAttempts);
        task.setTraceId(traceId);
        task.setLastError(null);
        task.setNextRunAt(LocalDateTime.now());
        documentIndexTaskRepository.save(task);
    }
}
