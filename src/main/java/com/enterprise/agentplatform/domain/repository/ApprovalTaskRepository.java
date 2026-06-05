package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.ApprovalTask;
import com.enterprise.agentplatform.domain.enums.ApprovalStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

    long countByStatus(ApprovalStatus status);

    List<ApprovalTask> findByApproverIdAndStatusOrderByIdDesc(Long approverId, ApprovalStatus status);

    List<ApprovalTask> findByStatusOrderByIdDesc(ApprovalStatus status);
}
