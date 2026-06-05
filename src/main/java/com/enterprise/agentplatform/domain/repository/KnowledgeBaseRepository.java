package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.KnowledgeBase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);
}
