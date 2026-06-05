package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.UserRole;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(Long userId);

    List<UserRole> findByUserIdIn(Collection<Long> userIds);

    List<UserRole> findByRoleId(Long roleId);
}
