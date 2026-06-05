import {
  ApprovalCommentTemplate,
  ApprovalTask,
  AuditLog,
  AuditLogFilters,
  AuditLogPage,
  TokenSessionAdmin,
  TokenSessionAdminPage,
  UserAdmin,
  UserAdminPage
} from '../api';
import { WorkspaceRouteId } from '../auth/permissions';
import { ApprovalQueueSection } from './ApprovalQueueSection';
import { AuditTimelineSection } from './AuditTimelineSection';
import { TokenSessionSection } from './TokenSessionSection';
import { UserStatusSection } from './UserStatusSection';

export function ApprovalAuditPanel({
  approvalTasks,
  approvalCommentTemplates,
  auditLogs,
  auditLogPage,
  auditLogFilters,
  users,
  userPage,
  usersLoading,
  tokenSessions,
  tokenSessionPage,
  tokenSessionsLoading,
  auditLogsLoading,
  canApprove,
  canManageUsers,
  canManageTokenSessions,
  canReadAuditLogs,
  visibleSection,
  onLoadApprovals,
  onDecideApproval,
  onLoadAudits,
  onAuditLogFiltersChange,
  onCopyTraceId,
  onChangeAuditPage,
  onLoadUsers,
  onChangeUserPage,
  onUnlockUser,
  onLoadTokenSessions,
  onChangeTokenSessionPage,
  onRevokeTokenSession,
  onRevokeUserTokenSessions
}: {
  approvalTasks: ApprovalTask[];
  approvalCommentTemplates: ApprovalCommentTemplate[];
  auditLogs: AuditLog[];
  auditLogPage: AuditLogPage | null;
  auditLogFilters: AuditLogFilters;
  users: UserAdmin[];
  userPage: UserAdminPage | null;
  usersLoading: boolean;
  tokenSessions: TokenSessionAdmin[];
  tokenSessionPage: TokenSessionAdminPage | null;
  tokenSessionsLoading: boolean;
  auditLogsLoading: boolean;
  canApprove: boolean;
  canManageUsers: boolean;
  canManageTokenSessions: boolean;
  canReadAuditLogs: boolean;
  visibleSection: Extract<WorkspaceRouteId, 'approvals' | 'users' | 'sessions' | 'audits'>;
  onLoadApprovals: () => void;
  onDecideApproval: (id: number, action: 'approve' | 'reject', templateCode: string, comment: string) => void;
  onLoadAudits: () => void;
  onAuditLogFiltersChange: (filters: AuditLogFilters) => void;
  onCopyTraceId: (traceId: string) => void;
  onChangeAuditPage: (page: number) => void;
  onLoadUsers: () => void;
  onChangeUserPage: (page: number) => void;
  onUnlockUser: (userId: number) => void;
  onLoadTokenSessions: () => void;
  onChangeTokenSessionPage: (page: number) => void;
  onRevokeTokenSession: (sessionId: number) => void;
  onRevokeUserTokenSessions: (userId: number) => void;
}) {
  return (
    <section className="section-grid">
      {visibleSection === 'approvals' && canApprove && (
        <ApprovalQueueSection
          approvalTasks={approvalTasks}
          approvalCommentTemplates={approvalCommentTemplates}
          onLoadApprovals={onLoadApprovals}
          onDecideApproval={onDecideApproval}
        />
      )}

      {visibleSection === 'users' && canManageUsers && (
        <UserStatusSection
          users={users}
          userPage={userPage}
          usersLoading={usersLoading}
          onLoadUsers={onLoadUsers}
          onChangeUserPage={onChangeUserPage}
          onUnlockUser={onUnlockUser}
          onRevokeUserTokenSessions={onRevokeUserTokenSessions}
        />
      )}

      {visibleSection === 'sessions' && canManageTokenSessions && (
        <TokenSessionSection
          tokenSessions={tokenSessions}
          tokenSessionPage={tokenSessionPage}
          tokenSessionsLoading={tokenSessionsLoading}
          onLoadTokenSessions={onLoadTokenSessions}
          onChangeTokenSessionPage={onChangeTokenSessionPage}
          onRevokeTokenSession={onRevokeTokenSession}
        />
      )}

      {visibleSection === 'audits' && canReadAuditLogs && (
        <AuditTimelineSection
          auditLogs={auditLogs}
          auditLogPage={auditLogPage}
          auditLogFilters={auditLogFilters}
          auditLogsLoading={auditLogsLoading}
          onLoadAudits={onLoadAudits}
          onAuditLogFiltersChange={onAuditLogFiltersChange}
          onCopyTraceId={onCopyTraceId}
          onChangeAuditPage={onChangeAuditPage}
        />
      )}
    </section>
  );
}
