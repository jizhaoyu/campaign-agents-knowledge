package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.Conversation;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
}
