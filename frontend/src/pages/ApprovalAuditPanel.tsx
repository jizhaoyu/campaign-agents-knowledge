import { ChangeEvent, FormEvent } from 'react';
import {
  ApprovalCommentTemplate,
  ApprovalTask,
  AuditLog,
  AuditLogPage,
  TokenSessionAdmin,
  TokenSessionAdminPage,
  UserAdmin,
  UserAdminPage
} from '../api';
import { ListEmpty } from '../components/ListEmpty';

export function ApprovalAuditPanel({
  approvalTasks,
  approvalCommentTemplates,
  auditLogs,
  auditLogPage,
  users,
  userPage,
  tokenSessions,
  tokenSessionPage,
  canApprove,
  canAudit,
  onLoadApprovals,
  onDecideApproval,
  onLoadAudits,
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
  users: UserAdmin[];
  userPage: UserAdminPage | null;
  tokenSessions: TokenSessionAdmin[];
  tokenSessionPage: TokenSessionAdminPage | null;
  canApprove: boolean;
  canAudit: boolean;
  onLoadApprovals: () => void;
  onDecideApproval: (id: number, action: 'approve' | 'reject', templateCode: string, comment: string) => void;
  onLoadAudits: () => void;
  onChangeAuditPage: (page: number) => void;
  onLoadUsers: () => void;
  onChangeUserPage: (page: number) => void;
  onUnlockUser: (userId: number) => void;
  onLoadTokenSessions: () => void;
  onChangeTokenSessionPage: (page: number) => void;
  onRevokeTokenSession: (sessionId: number) => void;
  onRevokeUserTokenSessions: (userId: number) => void;
}) {
  function decisionFormId(taskId: number, action: 'approve' | 'reject') {
    return `approval-${taskId}-${action}`;
  }

  function templatesFor(action: 'approve' | 'reject') {
    return approvalCommentTemplates.filter((template) => template.action === action);
  }

  function onTemplateChange(event: ChangeEvent<HTMLSelectElement>) {
    const selectedOption = event.currentTarget.selectedOptions.item(0);
    const form = event.currentTarget.form;
    if (!form || !selectedOption) {
      return;
    }
    const textarea = form.elements.namedItem('comment');
    if (textarea instanceof HTMLTextAreaElement) {
      textarea.value = selectedOption.dataset.comment || '';
    }
  }

  function submitDecision(event: FormEvent<HTMLFormElement>, taskId: number, action: 'approve' | 'reject') {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const templateCode = String(form.get('templateCode') || '');
    const comment = String(form.get('comment') || '').trim();
    onDecideApproval(taskId, action, templateCode, comment);
  }

  function pageText(page: { page: number; totalPages: number; totalItems: number } | null, visibleCount: number) {
    if (!page) {
      return `本页 ${visibleCount} 条`;
    }
    return `第 ${page.totalPages === 0 ? 0 : page.page + 1} / ${page.totalPages} 页，共 ${page.totalItems} 条`;
  }

  function renderPagination(
    label: string,
    page: { page: number; totalPages: number; totalItems: number; hasPrevious: boolean; hasNext: boolean } | null,
    visibleCount: number,
    onChangePage: (page: number) => void
  ) {
    return (
      <div className="pagination-bar" aria-label={label}>
        <span>{pageText(page, visibleCount)}</span>
        <div className="button-row">
          <button type="button" onClick={() => page && onChangePage(page.page - 1)} disabled={!page?.hasPrevious}>
            上一页
          </button>
          <button type="button" onClick={() => page && onChangePage(page.page + 1)} disabled={!page?.hasNext}>
            下一页
          </button>
        </div>
      </div>
    );
  }

  return (
    <section id="审批" className="section-grid">
      {canApprove && (
        <article className="card">
          <div className="card-heading">
            <span>05</span>
            <h2>审批队列</h2>
          </div>
          <button type="button" onClick={onLoadApprovals}>
            刷新待审批
          </button>
          <ListEmpty show={!approvalTasks.length} text="暂无待审批任务" />
          {approvalTasks.map((task) => (
            <div className="list-item actionable" key={task.id}>
              <strong>
                #{task.id} {task.targetType}
              </strong>
              <span>
                Target #{task.targetId} / {task.status}
              </span>
              <div className="approval-decision-grid">
                {(['approve', 'reject'] as const).map((action) => (
                  <form
                    className="approval-decision-form"
                    key={action}
                    id={decisionFormId(task.id, action)}
                    onSubmit={(event) => submitDecision(event, task.id, action)}
                  >
                    <label htmlFor={`${decisionFormId(task.id, action)}-template`}>
                      {action === 'approve' ? '通过模板' : '驳回模板'}
                    </label>
                    <select
                      id={`${decisionFormId(task.id, action)}-template`}
                      name="templateCode"
                      defaultValue=""
                      onChange={onTemplateChange}
                    >
                      <option value="">自定义备注</option>
                      {templatesFor(action).map((template) => (
                        <option key={template.code} value={template.code} data-comment={template.comment}>
                          {template.label}
                        </option>
                      ))}
                    </select>
                    <textarea
                      aria-label={action === 'approve' ? `审批 #${task.id} 通过备注` : `审批 #${task.id} 驳回备注`}
                      name="comment"
                      rows={3}
                      maxLength={255}
                      placeholder="可选择模板后再补充说明"
                    />
                    <button type="submit">{action === 'approve' ? '通过' : '驳回'}</button>
                  </form>
                ))}
              </div>
            </div>
          ))}
        </article>
      )}

      {canAudit && (
        <article id="用户" className="card">
          <div className="card-heading">
            <span>06</span>
            <h2>用户状态</h2>
          </div>
          <button type="button" onClick={onLoadUsers}>
            刷新用户
          </button>
          {renderPagination('用户分页', userPage, users.length, onChangeUserPage)}
          <ListEmpty show={!users.length} text="暂无用户数据" />
          {users.map((user) => (
            <div className="list-item actionable" key={user.id}>
              <strong>
                {user.displayName} / {user.username}
              </strong>
              <span>
                {user.status} / 失败 {user.failedLoginCount} 次 / {user.roles.join(' / ') || '无角色'}
              </span>
              <small>{user.lockedUntil ? `锁定至 ${user.lockedUntil}` : '未临时锁定'}</small>
              <button
                type="button"
                onClick={() => onUnlockUser(user.id)}
                disabled={!user.lockedUntil && user.failedLoginCount === 0}
              >
                解锁
              </button>
              <button type="button" onClick={() => onRevokeUserTokenSessions(user.id)}>
                吊销该用户会话
              </button>
            </div>
          ))}
        </article>
      )}

      {canAudit && (
        <article id="会话" className="card">
          <div className="card-heading">
            <span>07</span>
            <h2>Token 会话</h2>
          </div>
          <button type="button" onClick={onLoadTokenSessions}>
            刷新会话
          </button>
          {renderPagination('会话分页', tokenSessionPage, tokenSessions.length, onChangeTokenSessionPage)}
          <ListEmpty show={!tokenSessions.length} text="暂无会话数据" />
          {tokenSessions.map((session) => (
            <div className="list-item actionable" key={session.id}>
              <strong>
                #{session.id} / {session.username} / {session.active ? 'REFRESHABLE' : 'REVOKED'}
              </strong>
              <span>
                指纹 {session.tokenFingerprint} / Access {session.accessTokenActive ? '有效' : '过期'} / 角色{' '}
                {session.roleCodes || '无'}
              </span>
              <small>
                签发 {session.issuedAt} / Access 过期 {session.expiresAt} / Refresh 过期 {session.refreshExpiresAt}
              </small>
              <small>{session.lastRefreshedAt ? `最近刷新 ${session.lastRefreshedAt}` : '尚未刷新'}</small>
              <button type="button" onClick={() => onRevokeTokenSession(session.id)} disabled={!session.active}>
                吊销会话
              </button>
            </div>
          ))}
        </article>
      )}

      {canAudit && (
        <article id="审计" className="card">
          <div className="card-heading">
            <span>08</span>
            <h2>审计回看</h2>
          </div>
          <button type="button" onClick={onLoadAudits}>
            刷新审计
          </button>
          {renderPagination('审计分页', auditLogPage, auditLogs.length, onChangeAuditPage)}
          <ListEmpty show={!auditLogs.length} text="暂无审计数据" />
          {auditLogs.map((log) => (
            <div className="audit-row" key={log.id}>
              <strong>{log.eventType}</strong>
              <span>
                {log.targetType} #{log.targetId}
              </span>
              <code>{log.traceId}</code>
            </div>
          ))}
        </article>
      )}
    </section>
  );
}
