package com.enterprise.agentplatform.common.security;

public final class PermissionCode {

    public static final String KNOWLEDGE_MANAGE = "knowledge:manage";
    public static final String CHAT_USE = "chat:use";
    public static final String TICKET_DRAFT = "ticket:draft";
    public static final String TICKET_SUBMIT = "ticket:submit";
    public static final String TICKET_SIMILAR_READ = "ticket:similar:read";
    public static final String APPROVAL_REVIEW = "approval:review";
    public static final String DASHBOARD_READ = "dashboard:read";
    public static final String AUDIT_READ = "audit:read";
    public static final String USER_ADMIN = "user:admin";
    public static final String TOKEN_SESSION_ADMIN = "token-session:admin";

    private PermissionCode() {
    }
}
