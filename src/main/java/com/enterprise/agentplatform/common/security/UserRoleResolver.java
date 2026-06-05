package com.enterprise.agentplatform.common.security;

import com.enterprise.agentplatform.domain.entity.Role;
import com.enterprise.agentplatform.domain.entity.UserRole;
import com.enterprise.agentplatform.domain.repository.RoleRepository;
import com.enterprise.agentplatform.domain.repository.UserRoleRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserRoleResolver {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public UserRoleResolver(UserRoleRepository userRoleRepository, RoleRepository roleRepository) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    public Set<String> resolveRoleCodes(Long userId) {
        return resolveRoleCodesByUserId(List.of(userId)).getOrDefault(userId, Set.of());
    }

    public Map<Long, Set<String>> resolveRoleCodesByUserId(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Role> roleById = roleRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        return userRoleRepository.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.groupingBy(
                        UserRole::getUserId,
                        Collectors.collectingAndThen(Collectors.toList(), userRoles -> userRoles.stream()
                                .map(UserRole::getRoleId)
                                .map(roleById::get)
                                .filter(role -> role != null)
                                .map(Role::getCode)
                                .collect(Collectors.toUnmodifiableSet()))
                ));
    }
}
