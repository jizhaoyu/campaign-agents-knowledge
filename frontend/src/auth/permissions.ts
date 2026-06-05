import { Session } from '../api';

export type WorkspaceNavItem = {
  id: string;
  label: string;
  permissions?: string[];
};

export const Permission = {
  knowledgeManage: 'knowledge:manage',
  approvalReview: 'approval:review',
  dashboardRead: 'dashboard:read',
  auditRead: 'audit:read',
  userAdmin: 'user:admin',
  tokenSessionAdmin: 'token-session:admin'
} as const;

const fallbackPermissionsByRole: Record<string, string[]> = {
  ADMIN: [
    Permission.knowledgeManage,
    Permission.approvalReview,
    Permission.dashboardRead,
    Permission.auditRead,
    Permission.userAdmin,
    Permission.tokenSessionAdmin
  ],
  APPROVER: [Permission.approvalReview],
  USER: [],
  SUPPORT: []
};

export const workspaceNavItems: WorkspaceNavItem[] = [
  { id: '总览', label: '总览' },
  { id: '知识库', label: '知识库' },
  { id: '问答', label: '问答' },
  { id: '工单', label: '工单' },
  { id: '审批', label: '审批', permissions: [Permission.approvalReview] },
  { id: '用户', label: '用户', permissions: [Permission.userAdmin] },
  { id: '会话', label: '会话', permissions: [Permission.tokenSessionAdmin] },
  { id: '审计', label: '审计', permissions: [Permission.auditRead] }
];

export function hasRole(session: Session | null, role: string) {
  const targetRole = role.toUpperCase();
  return Boolean(session?.roles.some((currentRole) => currentRole.toUpperCase() === targetRole));
}

export function hasAnyRole(session: Session | null, roles: string[]) {
  return roles.some((role) => hasRole(session, role));
}

export function permissionsFor(session: Session | null) {
  const explicitPermissions = session?.permissions || [];
  const fallbackPermissions = session?.roles.flatMap((role) => fallbackPermissionsByRole[role.toUpperCase()] || []) || [];
  return new Set([...explicitPermissions, ...fallbackPermissions]);
}

export function hasPermission(session: Session | null, permission: string) {
  return permissionsFor(session).has(permission);
}

export function hasAnyPermission(session: Session | null, permissions: string[]) {
  return permissions.some((permission) => hasPermission(session, permission));
}

export function canManageKnowledge(session: Session | null) {
  return hasPermission(session, Permission.knowledgeManage);
}

export function canApprove(session: Session | null) {
  return hasPermission(session, Permission.approvalReview);
}

export function canReadDashboard(session: Session | null) {
  return hasPermission(session, Permission.dashboardRead);
}

export function canAdministerPlatform(session: Session | null) {
  return hasAnyPermission(session, [Permission.auditRead, Permission.userAdmin, Permission.tokenSessionAdmin]);
}

export function visibleWorkspaceNavItems(session: Session) {
  return workspaceNavItems.filter((item) => !item.permissions || hasAnyPermission(session, item.permissions));
}
