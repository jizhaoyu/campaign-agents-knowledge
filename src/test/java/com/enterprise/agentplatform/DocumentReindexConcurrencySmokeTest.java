package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.entity.KnowledgeBase;
import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import com.enterprise.agentplatform.domain.enums.ResourceStatus;
import com.enterprise.agentplatform.domain.repository.DocumentIndexTaskRepository;
import com.enterprise.agentplatform.domain.repository.DocumentRecordRepository;
import com.enterprise.agentplatform.domain.repository.KnowledgeBaseRepository;
import com.enterprise.agentplatform.knowledge.service.DocumentService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.document-index.poll-delay-ms=600000")
class DocumentReindexConcurrencySmokeTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private DocumentRecordRepository documentRecordRepository;

    @Autowired
    private DocumentIndexTaskRepository documentIndexTaskRepository;

    @Test
    void shouldEnqueueOnlyOneTaskForConcurrentReindexRequests() throws Exception {
        Long documentId = createIndexedDocument();

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> reindexCall = () -> {
                if (!startGate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("reindex race did not start");
                }
                documentService.reindex(documentId);
                return null;
            };
            Future<Void> firstReindex = executor.submit(reindexCall);
            Future<Void> secondReindex = executor.submit(reindexCall);
            startGate.countDown();

            firstReindex.get(10, TimeUnit.SECONDS);
            secondReindex.get(10, TimeUnit.SECONDS);

            assertThat(documentIndexTaskRepository.findByDocumentIdOrderByIdDesc(documentId))
                    .hasSize(1);
            DocumentRecord document = documentRecordRepository.findById(documentId).orElseThrow();
            assertThat(document.getIndexStatus()).isIn(ProcessingStatus.PENDING, ProcessingStatus.FAILED);
        } finally {
            executor.shutdownNow();
        }
    }

    private Long createIndexedDocument() {
        long suffix = Math.floorMod(System.nanoTime(), 1_000_000L);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName("Concurrent reindex KB " + suffix);
        knowledgeBase.setDescription("Concurrency test");
        knowledgeBase.setStatus(ResourceStatus.ACTIVE);
        knowledgeBase.setCreatedBy(1L);
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);

        DocumentRecord document = new DocumentRecord();
        document.setKnowledgeBaseId(knowledgeBase.getId());
        document.setFileName("reindex-race-" + suffix + ".md");
        document.setFileType("MD");
        document.setObjectKey("storage/reindex-race-" + suffix + ".md");
        document.setParseStatus(ProcessingStatus.SUCCESS);
        document.setIndexStatus(ProcessingStatus.SUCCESS);
        document.setUploadedBy(1L);
        return documentRecordRepository.save(document).getId();
    }
}
