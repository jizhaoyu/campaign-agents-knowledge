export type ApiResponse<T> = {
  code: string;
  message: string;
  data: T;
  traceId: string;
};

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
};

export type ApiErrorKind = 'auth' | 'validation' | 'business' | 'server' | 'network';

export type Session = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  refreshExpiresIn: number;
  username: string;
  displayName: string;
  roles: string[];
  permissions?: string[];
};

export type KnowledgeBase = {
  id: number;
  name: string;
  description: string | null;
  status: string;
};

export type DocumentUpload = {
  id: number;
  fileName: string;
  parseStatus: string;
  indexStatus: string;
  chunkCount: number;
  failureReason: string | null;
};

export type DocumentPage = PageResponse<DocumentUpload>;

export type Citation = {
  documentId: number;
  chunkId: number;
  documentName: string;
  snippet: string;
};

export type AskResult = {
  conversationId: number;
  answer: string;
  citations: Citation[];
  confidence: string;
  fallback: boolean;
};

export type ChatHistoryItem = AskResult & {
  knowledgeBaseId: number;
  question: string;
  createdAt: string;
  updatedAt: string;
};

export type TicketDraft = {
  conversationId: number;
  title: string;
  description: string;
  priority: string;
  suggestedAssigneeId: number | null;
};

export type SubmitTicketResult = {
  ticketId: number;
  status: string;
  approvalRequired: boolean;
  approvalTaskId: number | null;
};

export type SimilarTicket = {
  ticketId: number;
  title: string;
  priority: string;
  status: string;
  score: number;
  matchedKeywords: string[];
  matchSummary: string;
};

export type ApprovalTask = {
  id: number;
  targetType: string;
  targetId: number;
  approverId: number;
  status: string;
  comment: string | null;
};

export type ApprovalCommentTemplate = {
  code: string;
  action: 'approve' | 'reject';
  label: string;
  comment: string;
};

export type OperationsDashboard = {
  knowledgeBaseCount: number;
  documentCount: number;
  pendingIndexTaskCount: number;
  runningIndexTaskCount: number;
  failedIndexTaskCount: number;
  failedDocumentCount: number;
  pendingApprovalCount: number;
  activeHighRiskTicketCount: number;
  pendingHighRiskTicketCount: number;
  activeTokenSessionCount: number;
  totalIndexTaskCount: number;
  indexFailureRate: number;
  indexBacklogPressure: number;
  operationsBacklogCount: number;
  healthLevel: 'HEALTHY' | 'ATTENTION' | 'CRITICAL';
  alertCount: number;
  healthSummary: string;
  recommendedActions: string[];
  generatedAt: string;
};

export type AiRuntimeComponent = {
  enabled: boolean;
  modelAvailable: boolean;
  credentialConfigured: boolean;
  provider: string;
  baseUrl: string | null;
  path: string | null;
  model: string | null;
};

export type AiRuntimeStatus = {
  activeProfiles: string[];
  chat: AiRuntimeComponent;
  embedding: AiRuntimeComponent;
  readinessLevel: 'READY' | 'PARTIAL' | 'DISABLED';
  warnings: string[];
  generatedAt: string;
};

export type AuditLog = {
  id: number;
  actorId: number;
  eventType: string;
  targetType: string;
  targetId: number;
  traceId: string;
  payloadJson: string;
};

export type AuditLogPage = PageResponse<AuditLog>;

export type AuditLogFilters = {
  traceId: string;
  eventType: string;
  targetType: string;
  targetId: string;
};

export type UserAdmin = {
  id: number;
  username: string;
  displayName: string;
  status: string;
  failedLoginCount: number;
  lockedUntil: string | null;
  roles: string[];
};

export type UserAdminPage = PageResponse<UserAdmin>;

export type TokenSessionAdmin = {
  id: number;
  userId: number;
  username: string;
  tokenFingerprint: string;
  roleCodes: string;
  issuedAt: string;
  expiresAt: string;
  refreshExpiresAt: string;
  lastRefreshedAt: string | null;
  revokedAt: string | null;
  accessTokenActive: boolean;
  active: boolean;
};

export type TokenSessionAdminPage = PageResponse<TokenSessionAdmin>;

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly traceId?: string,
    readonly kind: ApiErrorKind = 'business'
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function isUnauthorizedError(error: unknown): error is ApiError {
  return isApiError(error) && error.kind === 'auth';
}

function classifyError(status: number, code?: string): ApiErrorKind {
  if (status === 0 || code === 'NETWORK_ERROR') {
    return 'network';
  }
  if (status === 401 || code === 'UNAUTHORIZED') {
    return 'auth';
  }
  if (status === 400 || status === 422 || code === 'VALIDATION_ERROR') {
    return 'validation';
  }
  if (status >= 500) {
    return 'server';
  }
  return 'business';
}

async function readPayload<T>(response: Response): Promise<Partial<ApiResponse<T>> | null> {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      return (await response.json()) as Partial<ApiResponse<T>>;
    } catch {
      return null;
    }
  }

  try {
    const text = await response.text();
    return text ? { message: text } : null;
  } catch {
    return null;
  }
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string
): Promise<ApiResponse<T>> {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  try {
    const response = await fetch(path, {
      ...options,
      headers
    });
    const payload = await readPayload<T>(response);
    const code = typeof payload?.code === 'string' ? payload.code : String(response.status);
    const message = typeof payload?.message === 'string' && payload.message ? payload.message : '请求失败';
    const traceId = typeof payload?.traceId === 'string' ? payload.traceId : undefined;
    if (!response.ok || code !== 'OK') {
      throw new ApiError(message, response.status, code, traceId, classifyError(response.status, code));
    }
    return payload as ApiResponse<T>;
  } catch (error) {
    if (isApiError(error)) {
      throw error;
    }
    if (error instanceof Error) {
      throw new ApiError(error.message || '网络请求失败', 0, 'NETWORK_ERROR', undefined, 'network');
    }
    throw new ApiError('网络请求失败', 0, 'NETWORK_ERROR', undefined, 'network');
  }
}
