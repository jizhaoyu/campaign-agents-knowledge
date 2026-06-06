package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.ApprovalTask;
import com.enterprise.agentplatform.domain.enums.ApprovalStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

    long countByStatus(ApprovalStatus status);

    List<ApprovalTask> findByApproverIdAndStatusOrderByIdDesc(Long approverId, ApprovalStatus status);

    List<ApprovalTask> findByStatusOrderByIdDesc(ApprovalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ApprovalTask> findLockedById(Long id);
}
