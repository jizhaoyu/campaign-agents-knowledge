package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
}
