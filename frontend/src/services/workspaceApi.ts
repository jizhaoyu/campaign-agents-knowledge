import {
  AiRuntimeStatus,
  ApiResponse,
  ApprovalCommentTemplate,
  ApprovalTask,
  AskResult,
  AuditLogFilters,
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
  request
} from '../api';

export type WorkspaceApiRequest = <T>(
  path: string,
  options?: RequestInit,
  accessToken?: string
) => Promise<ApiResponse<T>>;

export type DocumentQuery = {
  knowledgeBaseId: number;
  page: number;
  size: number;
  keyword?: string;
  indexStatus?: string;
};

export type KnowledgeBaseQuery = {
  keyword?: string;
};

export type SubmitTicketInput = {
  conversationId: number;
  title: string;
  description: string;
  priority: string;
  assigneeId: number | null;
};

export type ApprovalDecisionInput = {
  templateCode: string | null;
  comment: string;
};

export type AuditLogQuery = AuditLogFilters & {
  page: number;
  size: number;
};

function jsonBody(body: unknown): RequestInit {
  return {
    method: 'POST',
    body: JSON.stringify(body)
  };
}

function queryString(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      query.set(key, String(value));
    }
  });
  return query.toString();
}

export function loginUser(username: string, password: string) {
  return request<Session>('/api/v1/auth/login', jsonBody({ username, password }));
}

export function refreshLoginSession(refreshToken: string) {
  return request<Session>('/api/v1/auth/refresh', jsonBody({ refreshToken }));
}

export function logoutUser(accessToken: string) {
  return request<void>('/api/v1/auth/logout', { method: 'POST' }, accessToken);
}

export function listKnowledgeBases(apiRequest: WorkspaceApiRequest, accessToken: string, query: KnowledgeBaseQuery = {}) {
  const search = queryString({
    keyword: query.keyword?.trim()
  });
  return apiRequest<KnowledgeBase[]>(`/api/v1/knowledge-bases${search ? `?${search}` : ''}`, {}, accessToken);
}

export function createKnowledgeBase(
  apiRequest: WorkspaceApiRequest,
  accessToken: string,
  name: string,
  description: string | null
) {
  return apiRequest<KnowledgeBase>('/api/v1/knowledge-bases', jsonBody({ name, description }), accessToken);
}

export function listChatHistory(apiRequest: WorkspaceApiRequest, accessToken: string, limit = 10) {
  return apiRequest<ChatHistoryItem[]>(`/api/v1/chat/history?limit=${limit}`, {}, accessToken);
}

export function listDocuments(apiRequest: WorkspaceApiRequest, accessToken: string, query: DocumentQuery) {
  const search = queryString({
    knowledgeBaseId: query.knowledgeBaseId,
    page: query.page,
    size: query.size,
    keyword: query.keyword,
    indexStatus: query.indexStatus
  });
  return apiRequest<DocumentPage>(`/api/v1/documents?${search}`, {}, accessToken);
}

export function getDocument(apiRequest: WorkspaceApiRequest, accessToken: string, documentId: number) {
  return apiRequest<DocumentUpload>(`/api/v1/documents/${documentId}`, {}, accessToken);
}

export function uploadDocument(apiRequest: WorkspaceApiRequest, accessToken: string, form: FormData) {
  return apiRequest<DocumentUpload>('/api/v1/documents/upload', { method: 'POST', body: form }, accessToken);
}

export function reindexDocument(apiRequest: WorkspaceApiRequest, accessToken: string, documentId: number) {
  return apiRequest<DocumentUpload>(`/api/v1/documents/${documentId}/reindex`, { method: 'POST' }, accessToken);
}

export function retryFailedDocuments(apiRequest: WorkspaceApiRequest, accessToken: string, knowledgeBaseId: number) {
  return apiRequest<DocumentUpload[]>(
    `/api/v1/documents/retry-failed?knowledgeBaseId=${knowledgeBaseId}`,
    { method: 'POST' },
    accessToken
  );
}

export function deleteDocument(apiRequest: WorkspaceApiRequest, accessToken: string, documentId: number) {
  return apiRequest<void>(`/api/v1/documents/${documentId}`, { method: 'DELETE' }, accessToken);
}

export function askKnowledgeBase(
  apiRequest: WorkspaceApiRequest,
  accessToken: string,
  knowledgeBaseId: number,
  question: string
) {
  return apiRequest<AskResult>('/api/v1/chat/ask', jsonBody({ knowledgeBaseId, question }), accessToken);
}

export function createTicketDraft(apiRequest: WorkspaceApiRequest, accessToken: string, conversationId: number) {
  return apiRequest<TicketDraft>('/api/v1/tickets/draft', jsonBody({ conversationId }), accessToken);
}

export function submitTicket(apiRequest: WorkspaceApiRequest, accessToken: string, input: SubmitTicketInput) {
  return apiRequest<SubmitTicketResult>('/api/v1/tickets', jsonBody(input), accessToken);
}

export function listSimilarTickets(apiRequest: WorkspaceApiRequest, accessToken: string, conversationId: number) {
  return apiRequest<SimilarTicket[]>(`/api/v1/tickets/similar?conversationId=${conversationId}`, {}, accessToken);
}

export async function loadApprovalWorkspace(apiRequest: WorkspaceApiRequest, accessToken: string) {
  const [tasksResult, templatesResult] = await Promise.all([
    apiRequest<ApprovalTask[]>('/api/v1/approvals/pending', {}, accessToken),
    apiRequest<ApprovalCommentTemplate[]>('/api/v1/approvals/comment-templates', {}, accessToken)
  ]);
  return {
    tasks: tasksResult.data,
    templates: templatesResult.data
  };
}

export function decideApproval(
  apiRequest: WorkspaceApiRequest,
  accessToken: string,
  id: number,
  action: 'approve' | 'reject',
  input: ApprovalDecisionInput
) {
  return apiRequest<ApprovalTask>(`/api/v1/approvals/${id}/${action}`, jsonBody(input), accessToken);
}

export function getOperationsDashboard(apiRequest: WorkspaceApiRequest, accessToken: string) {
  return apiRequest<OperationsDashboard>('/api/v1/dashboard/operations', {}, accessToken);
}

export function getAiRuntimeStatus(apiRequest: WorkspaceApiRequest, accessToken: string) {
  return apiRequest<AiRuntimeStatus>('/api/v1/ai/runtime', {}, accessToken);
}

export function listAuditLogs(apiRequest: WorkspaceApiRequest, accessToken: string, query: AuditLogQuery) {
  const search = queryString({
    page: query.page,
    size: query.size,
    traceId: query.traceId.trim(),
    eventType: query.eventType.trim(),
    targetType: query.targetType.trim(),
    targetId: query.targetId.trim()
  });
  return apiRequest<AuditLogPage>(`/api/v1/audits?${search}`, {}, accessToken);
}

export function listUsers(apiRequest: WorkspaceApiRequest, accessToken: string, page: number, size = 8) {
  return apiRequest<UserAdminPage>(`/api/v1/users?page=${page}&size=${size}`, {}, accessToken);
}

export function unlockUser(apiRequest: WorkspaceApiRequest, accessToken: string, userId: number) {
  return apiRequest<UserAdmin>(`/api/v1/users/${userId}/unlock`, { method: 'POST' }, accessToken);
}

export function listTokenSessions(apiRequest: WorkspaceApiRequest, accessToken: string, page: number, size = 8) {
  return apiRequest<TokenSessionAdminPage>(
    `/api/v1/users/token-sessions?page=${page}&size=${size}`,
    {},
    accessToken
  );
}

export function revokeTokenSession(apiRequest: WorkspaceApiRequest, accessToken: string, sessionId: number) {
  return apiRequest<TokenSessionAdmin>(
    `/api/v1/users/token-sessions/${sessionId}/revoke`,
    { method: 'POST' },
    accessToken
  );
}

export function revokeUserTokenSessions(apiRequest: WorkspaceApiRequest, accessToken: string, userId: number) {
  return apiRequest<TokenSessionAdmin[]>(`/api/v1/users/${userId}/token-sessions/revoke`, { method: 'POST' }, accessToken);
}
