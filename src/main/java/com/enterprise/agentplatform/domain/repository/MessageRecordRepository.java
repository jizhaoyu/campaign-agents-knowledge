package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.MessageRecord;
import com.enterprise.agentplatform.domain.enums.MessageRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRecordRepository extends JpaRepository<MessageRecord, Long> {

    List<MessageRecord> findByConversationIdOrderByIdAsc(Long conversationId);

    Optional<MessageRecord> findFirstByConversationIdAndRoleOrderByIdDesc(Long conversationId, MessageRole role);
}
