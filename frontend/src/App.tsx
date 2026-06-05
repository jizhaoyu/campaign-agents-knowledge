import { FormEvent, useEffect, useRef, useState } from 'react';
import {
  ApprovalTask,
  ApprovalCommentTemplate,
  ApiError,
  AskResult,
  AuditLog,
  AuditLogPage,
  ChatHistoryItem,
  DocumentPage,
  DocumentUpload,
  KnowledgeBase,
  OperationsDashboard,
  Session,
  SimilarTicket,
  SubmitTicketResult,
  TicketDraft,
  TokenSessionAdmin,
  TokenSessionAdminPage,
  UserAdmin,
  UserAdminPage,
  isUnauthorizedError,
  request
} from './api';
import { NoticeBar } from './components/NoticeBar';
import { Shell } from './components/Shell';
import { canAdministerPlatform, canApprove, canManageKnowledge, canReadDashboard } from './auth/permissions';
import { useSession } from './hooks/useSession';
import { useNotice } from './hooks/useNotice';
import { ApprovalAuditPanel } from './pages/ApprovalAuditPanel';
import { ChatPanel } from './pages/ChatPanel';
import { DashboardMetrics } from './pages/DashboardMetrics';
import { KnowledgePanel } from './pages/KnowledgePanel';
import { LoginPage } from './pages/LoginPage';
import { TicketPanel } from './pages/TicketPanel';

function App() {
  const { session, saveSession, logout, isPending } = useSession();
  const { notice, setNotice, showError } = useNotice();
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState<number | ''>('');
  const [documents, setDocuments] = useState<DocumentUpload[]>([]);
  const [documentPage, setDocumentPage] = useState<DocumentPage | null>(null);
  const [documentKeyword, setDocumentKeyword] = useState('');
  const [documentStatusFilter, setDocumentStatusFilter] = useState('');
  const documentsRef = useRef<DocumentUpload[]>([]);
  const [askResult, setAskResult] = useState<AskResult | null>(null);
  const [chatHistory, setChatHistory] = useState<ChatHistoryItem[]>([]);
  const [ticketDraft, setTicketDraft] = useState<TicketDraft | null>(null);
  const [submitResult, setSubmitResult] = useState<SubmitTicketResult | null>(null);
  const [similarTickets, setSimilarTickets] = useState<SimilarTicket[]>([]);
  const [approvalTasks, setApprovalTasks] = useState<ApprovalTask[]>([]);
  const [approvalCommentTemplates, setApprovalCommentTemplates] = useState<ApprovalCommentTemplate[]>([]);
  const [operationsDashboard, setOperationsDashboard] = useState<OperationsDashboard | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [auditLogPage, setAuditLogPage] = useState<AuditLogPage | null>(null);
  const [users, setUsers] = useState<UserAdmin[]>([]);
  const [userPage, setUserPage] = useState<UserAdminPage | null>(null);
  const [tokenSessions, setTokenSessions] = useState<TokenSessionAdmin[]>([]);
  const [tokenSessionPage, setTokenSessionPage] = useState<TokenSessionAdminPage | null>(null);
  const refreshPromiseRef = useRef<Promise<Session> | null>(null);

  const token = session?.accessToken;
  const knowledgeManager = canManageKnowledge(session);
  const approvalReviewer = canApprove(session);
  const dashboardReader = canReadDashboard(session);
  const platformAdmin = canAdministerPlatform(session);

  function setDocumentList(nextDocuments: DocumentUpload[]) {
    documentsRef.current = nextDocuments;
    setDocuments(nextDocuments);
  }

  function setDocumentPageResult(nextPage: DocumentPage) {
    setDocumentPage(nextPage);
    setDocumentList(nextPage.items);
  }

  useEffect(() => {
    if (!session) {
      return;
    }
    void refreshKnowledgeBases(session.accessToken);
    void refreshChatHistory(session.accessToken);
    if (dashboardReader) {
      void refreshOperationsDashboard(session.accessToken);
    } else {
      setOperationsDashboard(null);
    }
  }, [dashboardReader, session]);

  useEffect(() => {
    if (!token || !selectedKnowledgeBaseId || !knowledgeManager) {
      setDocumentList([]);
      setDocumentPage(null);
      return;
    }
    void refreshDocuments(selectedKnowledgeBaseId, token);
  }, [documentKeyword, documentStatusFilter, knowledgeManager, selectedKnowledgeBaseId, token]);

  const hasPendingDocument = documents.some((document) => document.indexStatus === 'PENDING');

  useEffect(() => {
    if (!token || !selectedKnowledgeBaseId || !hasPendingDocument) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void refreshDocuments(selectedKnowledgeBaseId, token, { announceTransitions: true });
    }, 1000);
    return () => window.clearInterval(intervalId);
  }, [hasPendingDocument, selectedKnowledgeBaseId, token]);

  function resetWorkspaceState() {
    setKnowledgeBases([]);
    setSelectedKnowledgeBaseId('');
    setDocumentList([]);
    setDocumentPage(null);
    setDocumentKeyword('');
    setDocumentStatusFilter('');
    setAskResult(null);
    setChatHistory([]);
    setTicketDraft(null);
    setSubmitResult(null);
    setSimilarTickets([]);
    setApprovalTasks([]);
    setApprovalCommentTemplates([]);
    setOperationsDashboard(null);
    setAuditLogs([]);
    setAuditLogPage(null);
    setUsers([]);
    setUserPage(null);
    setTokenSessions([]);
    setTokenSessionPage(null);
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

  async function refreshSession() {
    if (!session?.refreshToken) {
      throw new ApiError('登录态已失效，请重新登录', 401, 'UNAUTHORIZED', undefined, 'auth');
    }
    if (!refreshPromiseRef.current) {
      refreshPromiseRef.current = request<Session>('/api/v1/auth/refresh', {
        method: 'POST',
        body: JSON.stringify({ refreshToken: session.refreshToken })
      })
        .then((result) => {
          saveSession(result.data);
          return result.data;
        })
        .finally(() => {
          refreshPromiseRef.current = null;
        });
    }
    return refreshPromiseRef.current;
  }

  async function authRequest<T>(path: string, options: RequestInit = {}, accessToken = token) {
    if (!accessToken) {
      throw new ApiError('未登录或登录态已失效', 401, 'UNAUTHORIZED', undefined, 'auth');
    }
    try {
      return await request<T>(path, options, accessToken);
    } catch (error) {
      if (!isUnauthorizedError(error)) {
        throw error;
      }
      const refreshedSession = await refreshSession();
      return request<T>(path, options, refreshedSession.accessToken);
    }
  }

  function requireText(form: FormData, field: string, label: string) {
    const value = String(form.get(field) || '').trim();
    if (!value) {
      setNotice({ tone: 'warn', text: `请填写${label}` });
      return null;
    }
    return value;
  }

  async function refreshKnowledgeBases(accessToken = token, preferredKnowledgeBaseId?: number) {
    if (!accessToken) {
      return;
    }
    try {
      const result = await authRequest<KnowledgeBase[]>('/api/v1/knowledge-bases', {}, accessToken);
      setKnowledgeBases(result.data);
      setSelectedKnowledgeBaseId((current) => preferredKnowledgeBaseId || current || result.data[0]?.id || '');
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function refreshChatHistory(accessToken = token) {
    if (!accessToken) {
      return;
    }
    try {
      const result = await authRequest<ChatHistoryItem[]>('/api/v1/chat/history?limit=10', {}, accessToken);
      setChatHistory(result.data);
    } catch (error) {
      handleRequestError(error);
    }
  }

  function replaceDocument(nextDocument: DocumentUpload) {
    mergeDocuments([nextDocument]);
  }

  function mergeDocuments(nextDocuments: DocumentUpload[]) {
    const documentById = new Map(documentsRef.current.map((document) => [document.id, document]));
    nextDocuments.forEach((document) => documentById.set(document.id, document));
    setDocumentList([...documentById.values()].sort((left, right) => right.id - left.id));
  }

  function announceDocumentTransitions(previousDocuments: DocumentUpload[], nextDocuments: DocumentUpload[]) {
    const previousStatusById = new Map(previousDocuments.map((document) => [document.id, document.indexStatus]));
    const finishedDocument = nextDocuments.find(
      (document) => previousStatusById.get(document.id) === 'PENDING' && document.indexStatus === 'SUCCESS'
    );
    if (finishedDocument) {
      setNotice({ tone: 'ok', text: `文档已索引：${finishedDocument.fileName}，${finishedDocument.chunkCount} 个切片` });
      return;
    }
    const failedDocument = nextDocuments.find(
      (document) => previousStatusById.get(document.id) === 'PENDING' && document.indexStatus === 'FAILED'
    );
    if (failedDocument) {
      setNotice({ tone: 'error', text: `文档索引失败：${failedDocument.failureReason || '未知原因'}` });
    }
  }

  async function refreshDocuments(
    knowledgeBaseId = selectedKnowledgeBaseId,
    accessToken = token,
    options: { announceTransitions?: boolean; announceRefresh?: boolean; page?: number } = {}
  ) {
    if (!accessToken || !knowledgeBaseId || !knowledgeManager) {
      return;
    }
    const previousDocuments = documentsRef.current;
    try {
      const page = options.page ?? documentPage?.page ?? 0;
      const params = new URLSearchParams({
        knowledgeBaseId: String(knowledgeBaseId),
        page: String(page),
        size: String(documentPage?.size ?? 10)
      });
      const keyword = documentKeyword.trim();
      if (keyword) {
        params.set('keyword', keyword);
      }
      if (documentStatusFilter) {
        params.set('indexStatus', documentStatusFilter);
      }
      const result = await authRequest<DocumentPage>(`/api/v1/documents?${params.toString()}`, {}, accessToken);
      const normalizedPage = result.data.totalPages > 0 && result.data.page >= result.data.totalPages
        ? result.data.totalPages - 1
        : result.data.page;
      if (normalizedPage !== result.data.page) {
        await refreshDocuments(knowledgeBaseId, accessToken, { ...options, page: normalizedPage });
        return;
      }
      setDocumentPageResult(result.data);
      if (options.announceTransitions) {
        announceDocumentTransitions(previousDocuments, result.data.items);
      }
      if (options.announceRefresh) {
        setNotice({ tone: 'ok', text: `文档列表已刷新：共 ${result.data.totalItems} 个文档` });
      }
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function refreshDocumentStatus(documentId: number, accessToken = token) {
    if (!accessToken || !knowledgeManager) {
      return;
    }
    try {
      const result = await authRequest<DocumentUpload>(`/api/v1/documents/${documentId}`, {}, accessToken);
      replaceDocument(result.data);
      if (result.data.indexStatus === 'SUCCESS') {
        setNotice({ tone: 'ok', text: `文档已索引：${result.data.fileName}，${result.data.chunkCount} 个切片` });
      }
      if (result.data.indexStatus === 'FAILED') {
        setNotice({ tone: 'error', text: `文档索引失败：${result.data.failureReason || '未知原因'}` });
      }
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function reindexDocument(documentId: number) {
    if (!token || !knowledgeManager) {
      return;
    }
    try {
      const result = await authRequest<DocumentUpload>(
        `/api/v1/documents/${documentId}/reindex`,
        { method: 'POST' },
        token
      );
      replaceDocument(result.data);
      await refreshDocuments(selectedKnowledgeBaseId, token);
      setNotice({
        tone: 'ok',
        text: result.data.indexStatus === 'PENDING' ? '已提交重建索引任务' : '文档仍在索引队列中'
      });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function retryFailedDocuments() {
    if (!token || !selectedKnowledgeBaseId || !knowledgeManager) {
      setNotice({ tone: 'warn', text: '请先选择知识库' });
      return;
    }
    try {
      const result = await authRequest<DocumentUpload[]>(
        `/api/v1/documents/retry-failed?knowledgeBaseId=${selectedKnowledgeBaseId}`,
        { method: 'POST' },
        token
      );
      mergeDocuments(result.data);
      await refreshDocuments(selectedKnowledgeBaseId, token);
      setNotice({
        tone: result.data.length ? 'ok' : 'warn',
        text: result.data.length ? `已重试 ${result.data.length} 个失败文档` : '暂无失败文档需要重试'
      });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function deleteDocument(documentId: number) {
    if (!token || !knowledgeManager) {
      return;
    }
    try {
      await authRequest<void>(`/api/v1/documents/${documentId}`, { method: 'DELETE' }, token);
      await refreshDocuments(selectedKnowledgeBaseId, token);
      setNotice({ tone: 'ok', text: '文档已删除' });
    } catch (error) {
      handleRequestError(error);
    }
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
      const result = await request<Session>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password })
      });
      saveSession(result.data);
      setNotice({ tone: 'ok', text: `已登录：${result.data.displayName}` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function createKnowledgeBase(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || !knowledgeManager) {
      return;
    }
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const name = requireText(form, 'name', '知识库名称');
    if (!name) {
      return;
    }
    const description = String(form.get('description') || '').trim();
    try {
      const result = await authRequest<KnowledgeBase>(
        '/api/v1/knowledge-bases',
        {
          method: 'POST',
          body: JSON.stringify({
            name,
            description: description || null
          })
        },
        token
      );
      formElement.reset();
      setNotice({ tone: 'ok', text: `知识库已创建：${result.data.name}` });
      setSelectedKnowledgeBaseId(result.data.id);
      setDocumentList([]);
      setDocumentPage(null);
      setDocumentKeyword('');
      setDocumentStatusFilter('');
      await refreshKnowledgeBases(token, result.data.id);
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function uploadDocument(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || !selectedKnowledgeBaseId || !knowledgeManager) {
      setNotice({ tone: 'warn', text: '请先选择知识库' });
      return;
    }
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    form.set('knowledgeBaseId', String(selectedKnowledgeBaseId));
    const file = form.get('file');
    if (!(file instanceof File) || !file.name) {
      setNotice({ tone: 'warn', text: '请选择要上传的文档' });
      return;
    }
    try {
      const result = await authRequest<DocumentUpload>(
        '/api/v1/documents/upload',
        {
          method: 'POST',
          body: form
        },
        token
      );
      formElement.reset();
      replaceDocument(result.data);
      await refreshDocuments(selectedKnowledgeBaseId, token);
      setNotice({ tone: 'ok', text: '文档已上传，正在后台解析和索引' });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function ask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || !selectedKnowledgeBaseId) {
      setNotice({ tone: 'warn', text: '请先选择知识库' });
      return;
    }
    const form = new FormData(event.currentTarget);
    const question = requireText(form, 'question', '问题');
    if (!question) {
      return;
    }
    try {
      const result = await authRequest<AskResult>(
        '/api/v1/chat/ask',
        {
          method: 'POST',
          body: JSON.stringify({
            knowledgeBaseId: selectedKnowledgeBaseId,
            question
          })
        },
        token
      );
      setAskResult(result.data);
      await refreshChatHistory(token);
      setTicketDraft(null);
      setSubmitResult(null);
      setNotice({ tone: 'ok', text: `已生成回答，置信度 ${result.data.confidence}` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function generateTicketDraft() {
    if (!token || !askResult) {
      return;
    }
    try {
      const result = await authRequest<TicketDraft>(
        '/api/v1/tickets/draft',
        {
          method: 'POST',
          body: JSON.stringify({ conversationId: askResult.conversationId })
        },
        token
      );
      setTicketDraft(result.data);
      setNotice({ tone: 'ok', text: '工单草稿已生成' });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function submitTicket(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || !ticketDraft) {
      return;
    }
    const form = new FormData(event.currentTarget);
    const title = requireText(form, 'title', '工单标题');
    const description = requireText(form, 'description', '工单描述');
    const priority = requireText(form, 'priority', '优先级');
    if (!title || !description || !priority) {
      return;
    }
    const assigneeValue = String(form.get('assigneeId') || '').trim();
    const assigneeId = assigneeValue ? Number(assigneeValue) : null;
    if (assigneeValue && Number.isNaN(assigneeId)) {
      setNotice({ tone: 'warn', text: '负责人 ID 必须是数字' });
      return;
    }
    try {
      const result = await authRequest<SubmitTicketResult>(
        '/api/v1/tickets',
        {
          method: 'POST',
          body: JSON.stringify({
            conversationId: ticketDraft.conversationId,
            title,
            description,
            priority,
            assigneeId
          })
        },
        token
      );
      setSubmitResult(result.data);
      setNotice({ tone: 'ok', text: `工单已提交：${result.data.status}` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function loadSimilarTickets() {
    if (!token || !askResult) {
      return;
    }
    try {
      const result = await authRequest<SimilarTicket[]>(
        `/api/v1/tickets/similar?conversationId=${askResult.conversationId}`,
        {},
        token
      );
      setSimilarTickets(result.data);
      setNotice({ tone: 'ok', text: `找到 ${result.data.length} 条相似工单` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  function restoreChatHistoryItem(item: ChatHistoryItem) {
    setSelectedKnowledgeBaseId(item.knowledgeBaseId);
    setAskResult({
      conversationId: item.conversationId,
      answer: item.answer,
      citations: item.citations,
      confidence: item.confidence,
      fallback: item.fallback
    });
    setTicketDraft(null);
    setSubmitResult(null);
    setNotice({ tone: 'ok', text: `已恢复会话 #${item.conversationId}` });
  }

  async function loadApprovals() {
    if (!token || !approvalReviewer) {
      return;
    }
    try {
      const [tasksResult, templatesResult] = await Promise.all([
        authRequest<ApprovalTask[]>('/api/v1/approvals/pending', {}, token),
        authRequest<ApprovalCommentTemplate[]>('/api/v1/approvals/comment-templates', {}, token)
      ]);
      setApprovalTasks(tasksResult.data);
      setApprovalCommentTemplates(templatesResult.data);
      setNotice({ tone: 'ok', text: `待审批 ${tasksResult.data.length} 条` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function refreshOperationsDashboard(
    accessToken = token,
    options: { announceRefresh?: boolean } = {}
  ) {
    if (!accessToken || !dashboardReader) {
      return;
    }
    try {
      const result = await authRequest<OperationsDashboard>('/api/v1/dashboard/operations', {}, accessToken);
      setOperationsDashboard(result.data);
      if (options.announceRefresh) {
        setNotice({ tone: 'ok', text: '运营指标已刷新' });
      }
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function decideApproval(id: number, action: 'approve' | 'reject', templateCode: string, comment: string) {
    if (!token || !approvalReviewer) {
      return;
    }
    try {
      await authRequest<ApprovalTask>(
        `/api/v1/approvals/${id}/${action}`,
        {
          method: 'POST',
          body: JSON.stringify({
            templateCode: templateCode || null,
            comment
          })
        },
        token
      );
      await loadApprovals();
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function loadAudits(page = auditLogPage?.page ?? 0) {
    if (!token || !platformAdmin) {
      return;
    }
    try {
      const result = await authRequest<AuditLogPage>(`/api/v1/audits?page=${page}&size=8`, {}, token);
      setAuditLogPage(result.data);
      setAuditLogs(result.data.items);
      setNotice({ tone: 'ok', text: `审计日志已刷新：共 ${result.data.totalItems} 条` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function loadUsers(page = userPage?.page ?? 0) {
    if (!token || !platformAdmin) {
      return;
    }
    try {
      const result = await authRequest<UserAdminPage>(`/api/v1/users?page=${page}&size=8`, {}, token);
      setUserPage(result.data);
      setUsers(result.data.items);
      setNotice({ tone: 'ok', text: `用户状态已刷新：共 ${result.data.totalItems} 个账号` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function unlockUser(userId: number) {
    if (!token || !platformAdmin) {
      return;
    }
    try {
      const result = await authRequest<UserAdmin>(`/api/v1/users/${userId}/unlock`, { method: 'POST' }, token);
      setUsers((currentUsers) => currentUsers.map((user) => (user.id === result.data.id ? result.data : user)));
      setNotice({ tone: 'ok', text: `账号已解锁：${result.data.username}` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function loadTokenSessions(page = tokenSessionPage?.page ?? 0) {
    if (!token || !platformAdmin) {
      return;
    }
    try {
      const result = await authRequest<TokenSessionAdminPage>(
        `/api/v1/users/token-sessions?page=${page}&size=8`,
        {},
        token
      );
      setTokenSessionPage(result.data);
      setTokenSessions(result.data.items);
      setNotice({ tone: 'ok', text: `Token 会话已刷新：共 ${result.data.totalItems} 条` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function revokeTokenSession(sessionId: number) {
    if (!token || !platformAdmin) {
      return;
    }
    try {
      const result = await authRequest<TokenSessionAdmin>(
        `/api/v1/users/token-sessions/${sessionId}/revoke`,
        { method: 'POST' },
        token
      );
      setTokenSessions((currentSessions) =>
        currentSessions.map((session) => (session.id === result.data.id ? result.data : session))
      );
      setNotice({ tone: 'ok', text: `Token 会话已吊销：#${result.data.id}` });
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function revokeUserTokenSessions(userId: number) {
    if (!token || !platformAdmin) {
      return;
    }
    try {
      const result = await authRequest<TokenSessionAdmin[]>(
        `/api/v1/users/${userId}/token-sessions/revoke`,
        { method: 'POST' },
        token
      );
      setTokenSessions((currentSessions) => {
        const updatedById = new Map(result.data.map((session) => [session.id, session]));
        return currentSessions.map((session) => updatedById.get(session.id) || session);
      });
      setNotice({ tone: 'ok', text: `用户 Token 会话已批量吊销：${result.data.length} 条` });
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
      await request<void>('/api/v1/auth/logout', { method: 'POST' }, logoutToken);
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
        <Shell session={session} onLogout={() => void handleLogout()}>
          <DashboardMetrics
            knowledgeBases={knowledgeBases}
            askResult={askResult}
            submitResult={submitResult}
            operationsDashboard={operationsDashboard}
            canReadDashboard={dashboardReader}
            onRefreshOperationsDashboard={() => void refreshOperationsDashboard(token, { announceRefresh: true })}
          />
          <KnowledgePanel
            knowledgeBases={knowledgeBases}
            selectedKnowledgeBaseId={selectedKnowledgeBaseId}
            documents={documents}
            documentPage={documentPage}
            documentKeyword={documentKeyword}
            documentStatusFilter={documentStatusFilter}
            canManageKnowledge={knowledgeManager}
            onSelectKnowledgeBase={setSelectedKnowledgeBaseId}
            onDocumentKeywordChange={(keyword) => {
              setDocumentKeyword(keyword);
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
          <ChatPanel
            askResult={askResult}
            chatHistory={chatHistory}
            onAsk={ask}
            onRefreshHistory={() => void refreshChatHistory(token)}
            onRestoreHistoryItem={restoreChatHistoryItem}
          />
          <TicketPanel
            askResult={askResult}
            ticketDraft={ticketDraft}
            submitResult={submitResult}
            similarTickets={similarTickets}
            onGenerateDraft={generateTicketDraft}
            onLoadSimilarTickets={loadSimilarTickets}
            onSubmitTicket={submitTicket}
          />
          {(approvalReviewer || platformAdmin) && (
            <ApprovalAuditPanel
              approvalTasks={approvalTasks}
              approvalCommentTemplates={approvalCommentTemplates}
              auditLogs={auditLogs}
              auditLogPage={auditLogPage}
              users={users}
              userPage={userPage}
              tokenSessions={tokenSessions}
              tokenSessionPage={tokenSessionPage}
              canApprove={approvalReviewer}
              canAudit={platformAdmin}
              onLoadApprovals={loadApprovals}
              onDecideApproval={(id, action, templateCode, comment) =>
                void decideApproval(id, action, templateCode, comment)
              }
              onLoadAudits={() => void loadAudits()}
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
