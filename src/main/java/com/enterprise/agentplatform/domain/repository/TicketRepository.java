package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.Ticket;
import com.enterprise.agentplatform.domain.enums.TicketPriority;
import com.enterprise.agentplatform.domain.enums.TicketStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    long countByPriorityAndStatusIn(TicketPriority priority, Collection<TicketStatus> statuses);

    long countByPriorityAndStatus(TicketPriority priority, TicketStatus status);

    List<Ticket> findByCreatedByOrderByIdDesc(Long createdBy);

    List<Ticket> findByTitleContainingIgnoreCaseOrderByIdDesc(String keyword, Pageable pageable);
}
