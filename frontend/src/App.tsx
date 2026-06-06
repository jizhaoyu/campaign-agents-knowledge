import { FormEvent, useEffect } from 'react';
import {
  ApiError,
  Session,
  isUnauthorizedError
} from './api';
import { NoticeBar } from './components/NoticeBar';
import { Shell } from './components/Shell';
import {
  canAdministerTokenSessions,
  canAdministerUsers,
  canApprove,
  canManageKnowledge,
  canReadAudits,
  canReadDashboard
} from './auth/permissions';
import { useSession } from './hooks/useSession';
import { useNotice } from './hooks/useNotice';
import { useAdminWorkspace } from './hooks/useAdminWorkspace';
import { useAuthenticatedRequest } from './hooks/useAuthenticatedRequest';
import { useApprovalWorkspace } from './hooks/useApprovalWorkspace';
import { useChatWorkspace } from './hooks/useChatWorkspace';
import { useClipboardActions } from './hooks/useClipboardActions';
import { useKnowledgeWorkspace } from './hooks/useKnowledgeWorkspace';
import { useOperationsWorkspace } from './hooks/useOperationsWorkspace';
import { useTicketWorkspace } from './hooks/useTicketWorkspace';
import { useWorkspaceRoute } from './hooks/useWorkspaceRoute';
import { ApprovalAuditPanel } from './pages/ApprovalAuditPanel';
import { AiRuntimePanel } from './pages/AiRuntimePanel';
import { ChatPanel } from './pages/ChatPanel';
import { DashboardMetrics } from './pages/DashboardMetrics';
import { KnowledgePanel } from './pages/KnowledgePanel';
import { LoginPage } from './pages/LoginPage';
import { TicketPanel } from './pages/TicketPanel';
import * as workspaceApi from './services/workspaceApi';

function App() {
  const { session, saveSession, logout, isPending } = useSession();
  const { routeId, navigate } = useWorkspaceRoute(session);
  const { authRequest } = useAuthenticatedRequest({ session, saveSession });
  const { notice, setNotice, showError } = useNotice();
  const { copyTraceId, copyCitation, copyTicketId } = useClipboardActions({ setNotice });

  const token = session?.accessToken;
  const knowledgeManager = canManageKnowledge(session);
  const approvalReviewer = canApprove(session);
  const dashboardReader = canReadDashboard(session);
  const userAdmin = canAdministerUsers(session);
  const tokenSessionAdmin = canAdministerTokenSessions(session);
  const auditReader = canReadAudits(session);
  const knowledgeRouteActive = routeId === 'knowledge';
  const {
    knowledgeBases,
    selectedKnowledgeBaseId,
    knowledgeBaseKeyword,
    documents,
    documentPage,
    documentPageSize,
    documentKeyword,
    documentStatusFilter,
    documentsLoading,
    uploadDocumentLoading,
    retryFailedDocumentsLoading,
    reindexingDocumentIds,
    deletingDocumentIds,
    setSelectedKnowledgeBaseId,
    setKnowledgeBaseKeyword,
    setDocumentKeyword,
    setDocumentPageSize,
    setDocumentStatusFilter,
    setDocumentPage,
    refreshDocuments,
    refreshDocumentStatus,
    reindexDocument,
    retryFailedDocuments,
    deleteDocument,
    createKnowledgeBase,
    uploadDocument,
    resetKnowledgeWorkspace
  } = useKnowledgeWorkspace({
    token,
    knowledgeManager,
    knowledgeRouteActive,
    authRequest,
    setNotice,
    handleRequestError
  });
  const {
    askResult,
    chatHistory,
    asking,
    chatHistoryLoading,
    refreshChatHistory,
    ask,
    restoreChatHistoryItem,
    resetChatWorkspace
  } = useChatWorkspace({
    token,
    selectedKnowledgeBaseId,
    authRequest,
    setSelectedKnowledgeBaseId,
    clearTicketState,
    setNotice,
    handleRequestError,
    requireText
  });
  const {
    ticketDraft,
    submitResult,
    similarTickets,
    ticketDraftLoading,
    ticketSubmitLoading,
    similarTicketsLoading,
    generateTicketDraft,
    submitTicket,
    loadSimilarTickets,
    resetTicketWorkspace
  } = useTicketWorkspace({
    token,
    askResult,
    authRequest,
    setNotice,
    handleRequestError,
    requireText
  });
  const {
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
    unlockingUserIds,
    revokingTokenSessionIds,
    revokingUserTokenSessionIds,
    updateAuditLogFilters,
    loadAudits,
    loadUsers,
    unlockUser,
    loadTokenSessions,
    revokeTokenSession,
    revokeUserTokenSessions,
    resetAdminWorkspace
  } = useAdminWorkspace({
    token,
    authRequest,
    userAdmin,
    tokenSessionAdmin,
    auditReader,
    setNotice,
    handleRequestError
  });
  const {
    approvalTasks,
    approvalCommentTemplates,
    approvalsLoading,
    decidingApprovalKeys,
    loadApprovals,
    decideApproval,
    resetApprovalWorkspace
  } = useApprovalWorkspace({
    token,
    authRequest,
    approvalReviewer,
    setNotice,
    handleRequestError
  });
  const {
    operationsDashboard,
    aiRuntimeStatus,
    operationsDashboardLoading,
    aiRuntimeStatusLoading,
    operationsDashboardError,
    aiRuntimeStatusError,
    refreshOperationsDashboard,
    refreshAiRuntimeStatus,
    resetOperationsWorkspace
  } = useOperationsWorkspace({
    token,
    routeId,
    authRequest,
    dashboardReader,
    setNotice,
    handleRequestError
  });

  useEffect(() => {
    if (!session) {
      return;
    }
    void refreshChatHistory(session.accessToken);
  }, [session]);

  function resetWorkspaceState() {
    resetKnowledgeWorkspace();
    resetChatWorkspace();
    resetTicketWorkspace();
    resetApprovalWorkspace();
    resetOperationsWorkspace();
    resetAdminWorkspace();
  }

  function handleAuthExpired(error?: ApiError) {
    logout();
    resetWorkspaceState();
    const traceId = error?.traceId ? `，traceId: ${error.traceId}` : '';
    setNotice({ tone: 'warn', text: `登录态已失效，请重新登录${traceId}` });
  }

  function handleRequestError(error: unknown) {
    if (isUnauthorizedError(error)) {
      handleAuthExpired(error);
      return;
    }
    showError(error);
  }

  function requireText(form: FormData, field: string, label: string) {
    const value = String(form.get(field) || '').trim();
    if (!value) {
      setNotice({ tone: 'warn', text: `请填写${label}` });
      return null;
    }
    return value;
  }

  function clearTicketState() {
    resetTicketWorkspace();
  }

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const username = requireText(form, 'username', '账号');
    const password = requireText(form, 'password', '密码');
    if (!username || !password) {
      return;
    }
    try {
      const result = await workspaceApi.loginUser(username, password);
      saveSession(result.data);
      setNotice({ tone: 'ok', text: `已登录：${result.data.displayName}` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function handleLogout() {
    const logoutToken = token;
    logout();
    resetWorkspaceState();
    if (!logoutToken) {
      return;
    }
    try {
      await workspaceApi.logoutUser(logoutToken);
      setNotice({ tone: 'ok', text: '已退出登录' });
    } catch (error) {
      if (isUnauthorizedError(error)) {
        const traceId = error.traceId ? `，traceId: ${error.traceId}` : '';
        setNotice({ tone: 'warn', text: `服务端会话已失效，本地登录态已清理${traceId}` });
        return;
      }
      const message = error instanceof ApiError ? error.message : '网络请求失败';
      setNotice({ tone: 'warn', text: `本地登录态已清理，服务端登出未确认：${message}` });
    }
  }

  return (
    <>
      <div className="notice-layer">
        <NoticeBar notice={notice} />
      </div>
      {!session ? (
        <LoginPage onLogin={login} />
      ) : (
        <Shell
          session={session}
          activeRouteId={routeId}
          onNavigate={navigate}
          onLogout={() => void handleLogout()}
        >
          {routeId === 'dashboard' && (
            <DashboardMetrics
              knowledgeBases={knowledgeBases}
              askResult={askResult}
              submitResult={submitResult}
              operationsDashboard={operationsDashboard}
              operationsDashboardLoading={operationsDashboardLoading}
              operationsDashboardError={operationsDashboardError}
              canReadDashboard={dashboardReader}
              onRefreshOperationsDashboard={() => void refreshOperationsDashboard(token, { announceRefresh: true })}
            />
          )}
          {routeId === 'knowledge' && (
            <KnowledgePanel
              knowledgeBases={knowledgeBases}
              selectedKnowledgeBaseId={selectedKnowledgeBaseId}
              knowledgeBaseKeyword={knowledgeBaseKeyword}
              documents={documents}
              documentPage={documentPage}
              documentPageSize={documentPageSize}
              documentKeyword={documentKeyword}
              documentStatusFilter={documentStatusFilter}
              documentsLoading={documentsLoading}
              uploadDocumentLoading={uploadDocumentLoading}
              retryFailedDocumentsLoading={retryFailedDocumentsLoading}
              reindexingDocumentIds={reindexingDocumentIds}
              deletingDocumentIds={deletingDocumentIds}
              canManageKnowledge={knowledgeManager}
              onSelectKnowledgeBase={setSelectedKnowledgeBaseId}
              onKnowledgeBaseKeywordChange={setKnowledgeBaseKeyword}
              onDocumentKeywordChange={(keyword) => {
                setDocumentKeyword(keyword);
                setDocumentPage(null);
              }}
              onDocumentPageSizeChange={(size) => {
                setDocumentPageSize(size);
                setDocumentPage(null);
              }}
              onDocumentStatusFilterChange={(status) => {
                setDocumentStatusFilter(status);
                setDocumentPage(null);
              }}
              onCreateKnowledgeBase={createKnowledgeBase}
              onUploadDocument={uploadDocument}
              onRefreshDocuments={() => void refreshDocuments(selectedKnowledgeBaseId, token, { announceRefresh: true })}
              onChangeDocumentPage={(page) => void refreshDocuments(selectedKnowledgeBaseId, token, { page })}
              onRefreshDocument={(documentId) => void refreshDocumentStatus(documentId)}
              onReindexDocument={(documentId) => void reindexDocument(documentId)}
              onRetryFailedDocuments={() => void retryFailedDocuments()}
              onDeleteDocument={(documentId) => void deleteDocument(documentId)}
            />
          )}
          {routeId === 'chat' && (
            <ChatPanel
              askResult={askResult}
              chatHistory={chatHistory}
              asking={asking}
              chatHistoryLoading={chatHistoryLoading}
              onAsk={ask}
              onRefreshHistory={() => void refreshChatHistory(token)}
              onRestoreHistoryItem={restoreChatHistoryItem}
              onCopyCitation={(citation) => void copyCitation(citation)}
            />
          )}
          {routeId === 'tickets' && (
            <TicketPanel
              askResult={askResult}
              ticketDraft={ticketDraft}
              submitResult={submitResult}
              similarTickets={similarTickets}
              ticketDraftLoading={ticketDraftLoading}
              ticketSubmitLoading={ticketSubmitLoading}
              similarTicketsLoading={similarTicketsLoading}
              onGenerateDraft={generateTicketDraft}
              onLoadSimilarTickets={loadSimilarTickets}
              onSubmitTicket={submitTicket}
              onCopyTicketId={(ticketId) => void copyTicketId(ticketId)}
            />
          )}
          {routeId === 'ai-config' && (
            <AiRuntimePanel
              aiRuntimeStatus={aiRuntimeStatus}
              aiRuntimeStatusLoading={aiRuntimeStatusLoading}
              aiRuntimeStatusError={aiRuntimeStatusError}
              canReadDashboard={dashboardReader}
              onRefresh={() => void refreshAiRuntimeStatus(token, { announceRefresh: true })}
            />
          )}
          {(routeId === 'approvals' || routeId === 'users' || routeId === 'sessions' || routeId === 'audits') && (
            <ApprovalAuditPanel
              approvalTasks={approvalTasks}
              approvalCommentTemplates={approvalCommentTemplates}
              approvalsLoading={approvalsLoading}
              decidingApprovalKeys={decidingApprovalKeys}
              auditLogs={auditLogs}
              auditLogPage={auditLogPage}
              auditLogFilters={auditLogFilters}
              users={users}
              userPage={userPage}
              usersLoading={usersLoading}
              tokenSessions={tokenSessions}
              tokenSessionPage={tokenSessionPage}
              tokenSessionsLoading={tokenSessionsLoading}
              auditLogsLoading={auditLogsLoading}
              unlockingUserIds={unlockingUserIds}
              revokingTokenSessionIds={revokingTokenSessionIds}
              revokingUserTokenSessionIds={revokingUserTokenSessionIds}
              canApprove={approvalReviewer}
              canManageUsers={userAdmin}
              canManageTokenSessions={tokenSessionAdmin}
              canReadAuditLogs={auditReader}
              visibleSection={routeId}
              onLoadApprovals={loadApprovals}
              onDecideApproval={(id, action, templateCode, comment) =>
                void decideApproval(id, action, templateCode, comment)
              }
              onLoadAudits={() => void loadAudits()}
              onAuditLogFiltersChange={updateAuditLogFilters}
              onCopyTraceId={(traceId) => void copyTraceId(traceId)}
              onChangeAuditPage={(page) => void loadAudits(page)}
              onLoadUsers={() => void loadUsers()}
              onChangeUserPage={(page) => void loadUsers(page)}
              onUnlockUser={(userId) => void unlockUser(userId)}
              onLoadTokenSessions={() => void loadTokenSessions()}
              onChangeTokenSessionPage={(page) => void loadTokenSessions(page)}
              onRevokeTokenSession={(sessionId) => void revokeTokenSession(sessionId)}
              onRevokeUserTokenSessions={(userId) => void revokeUserTokenSessions(userId)}
            />
          )}
          {isPending && <p className="hint">界面状态更新中...</p>}
        </Shell>
      )}
    </>
  );
}

export default App;
