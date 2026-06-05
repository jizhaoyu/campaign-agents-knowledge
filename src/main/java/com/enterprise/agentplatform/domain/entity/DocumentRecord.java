package com.enterprise.agentplatform.domain.entity;

import com.enterprise.agentplatform.domain.enums.ProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "document_record")
public class DocumentRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 32)
    private String fileType;

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 16)
    private ProcessingStatus parseStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_status", nullable = false, length = 16)
    private ProcessingStatus indexStatus;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;
}
