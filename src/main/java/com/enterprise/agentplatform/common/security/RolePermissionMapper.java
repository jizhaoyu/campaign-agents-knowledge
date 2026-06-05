package com.enterprise.agentplatform.common.security;

import com.enterprise.agentplatform.domain.enums.RoleCode;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class RolePermissionMapper {

    private final Map<RoleCode, Set<String>> permissionsByRole = new EnumMap<>(RoleCode.class);

    public RolePermissionMapper() {
        permissionsByRole.put(RoleCode.ADMIN, Set.of(
                PermissionCode.KNOWLEDGE_MANAGE,
                PermissionCode.CHAT_USE,
                PermissionCode.TICKET_DRAFT,
                PermissionCode.TICKET_SUBMIT,
                PermissionCode.TICKET_SIMILAR_READ,
                PermissionCode.APPROVAL_REVIEW,
                PermissionCode.DASHBOARD_READ,
                PermissionCode.AUDIT_READ,
                PermissionCode.USER_ADMIN,
                PermissionCode.TOKEN_SESSION_ADMIN
        ));
        permissionsByRole.put(RoleCode.USER, Set.of(
                PermissionCode.CHAT_USE,
                PermissionCode.TICKET_DRAFT,
                PermissionCode.TICKET_SUBMIT,
                PermissionCode.TICKET_SIMILAR_READ
        ));
        permissionsByRole.put(RoleCode.SUPPORT, Set.of(
                PermissionCode.CHAT_USE,
                PermissionCode.TICKET_DRAFT,
                PermissionCode.TICKET_SUBMIT,
                PermissionCode.TICKET_SIMILAR_READ
        ));
        permissionsByRole.put(RoleCode.APPROVER, Set.of(
                PermissionCode.CHAT_USE,
                PermissionCode.TICKET_SIMILAR_READ,
                PermissionCode.APPROVAL_REVIEW
        ));
    }

    public Set<String> permissionsFor(Set<String> roles) {
        Set<String> permissions = new TreeSet<>();
        for (String role : roles) {
            RoleCode roleCode = parseRole(role);
            if (roleCode != null) {
                permissions.addAll(permissionsByRole.getOrDefault(roleCode, Set.of()));
            }
        }
        return Set.copyOf(permissions);
    }

    private RoleCode parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return RoleCode.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
