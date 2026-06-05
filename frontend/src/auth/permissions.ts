import { Session } from '../api';

export type WorkspaceNavItem = {
  id: WorkspaceRouteId;
  label: string;
  path: string;
  permissions?: string[];
};

export type WorkspaceRouteId =
  | 'dashboard'
  | 'knowledge'
  | 'chat'
  | 'tickets'
  | 'approvals'
  | 'ai-config'
  | 'users'
  | 'sessions'
  | 'audits';

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

export const defaultWorkspaceRouteId: WorkspaceRouteId = 'dashboard';

export const workspaceNavItems: WorkspaceNavItem[] = [
  { id: 'dashboard', label: '总览', path: '/dashboard' },
  { id: 'knowledge', label: '知识库', path: '/knowledge' },
  { id: 'chat', label: '问答', path: '/chat' },
  { id: 'tickets', label: '工单', path: '/tickets' },
  { id: 'approvals', label: '审批', path: '/approvals', permissions: [Permission.approvalReview] },
  { id: 'ai-config', label: 'AI配置', path: '/ai-config', permissions: [Permission.dashboardRead] },
  { id: 'users', label: '用户', path: '/users', permissions: [Permission.userAdmin] },
  { id: 'sessions', label: '会话', path: '/sessions', permissions: [Permission.tokenSessionAdmin] },
  { id: 'audits', label: '审计', path: '/audits', permissions: [Permission.auditRead] }
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

export function canAdministerUsers(session: Session | null) {
  return hasPermission(session, Permission.userAdmin);
}

export function canAdministerTokenSessions(session: Session | null) {
  return hasPermission(session, Permission.tokenSessionAdmin);
}

export function canReadAudits(session: Session | null) {
  return hasPermission(session, Permission.auditRead);
}

export function canAdministerPlatform(session: Session | null) {
  return hasAnyPermission(session, [Permission.auditRead, Permission.userAdmin, Permission.tokenSessionAdmin]);
}

export function visibleWorkspaceNavItems(session: Session | null) {
  return workspaceNavItems.filter((item) => !item.permissions || hasAnyPermission(session, item.permissions));
}

export function workspaceRouteFromPath(pathname: string): WorkspaceRouteId {
  return workspaceNavItems.find((item) => item.path === pathname)?.id ?? defaultWorkspaceRouteId;
}

export function workspacePathFor(routeId: WorkspaceRouteId) {
  return workspaceNavItems.find((item) => item.id === routeId)?.path ?? '/dashboard';
}
