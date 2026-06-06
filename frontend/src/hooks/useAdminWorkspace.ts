import { useRef, useState } from 'react';
import {
  AuditLog,
  AuditLogFilters,
  AuditLogPage,
  TokenSessionAdmin,
  TokenSessionAdminPage,
  UserAdmin,
  UserAdminPage
} from '../api';
import * as workspaceApi from '../services/workspaceApi';
import { WorkspaceApiRequest } from '../services/workspaceApi';

type Notice = {
  tone: 'ok' | 'warn' | 'error';
  text: string;
};

const emptyAuditLogFilters = {
  traceId: '',
  eventType: '',
  targetType: '',
  targetId: ''
};

export function useAdminWorkspace({
  token,
  authRequest,
  userAdmin,
  tokenSessionAdmin,
  auditReader,
  setNotice,
  handleRequestError
}: {
  token?: string;
  authRequest: WorkspaceApiRequest;
  userAdmin: boolean;
  tokenSessionAdmin: boolean;
  auditReader: boolean;
  setNotice: (notice: Notice) => void;
  handleRequestError: (error: unknown) => void;
}) {
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [auditLogPage, setAuditLogPage] = useState<AuditLogPage | null>(null);
  const [auditLogFilters, setAuditLogFilters] = useState<AuditLogFilters>(emptyAuditLogFilters);
  const [users, setUsers] = useState<UserAdmin[]>([]);
  const [userPage, setUserPage] = useState<UserAdminPage | null>(null);
  const [usersLoading, setUsersLoading] = useState(false);
  const [tokenSessions, setTokenSessions] = useState<TokenSessionAdmin[]>([]);
  const [tokenSessionPage, setTokenSessionPage] = useState<TokenSessionAdminPage | null>(null);
  const [tokenSessionsLoading, setTokenSessionsLoading] = useState(false);
  const [auditLogsLoading, setAuditLogsLoading] = useState(false);
  const [unlockingUserIds, setUnlockingUserIds] = useState<number[]>([]);
  const [revokingTokenSessionIds, setRevokingTokenSessionIds] = useState<number[]>([]);
  const [revokingUserTokenSessionIds, setRevokingUserTokenSessionIds] = useState<number[]>([]);
  const unlockingUserIdRef = useRef(new Set<number>());
  const revokingTokenSessionIdRef = useRef(new Set<number>());
  const revokingUserTokenSessionIdRef = useRef(new Set<number>());

  function resetAdminWorkspace() {
    setAuditLogs([]);
    setAuditLogPage(null);
    setAuditLogFilters(emptyAuditLogFilters);
    setUsers([]);
    setUserPage(null);
    setUsersLoading(false);
    setTokenSessions([]);
    setTokenSessionPage(null);
    setTokenSessionsLoading(false);
    setAuditLogsLoading(false);
    setUnlockingUserIds([]);
    setRevokingTokenSessionIds([]);
    setRevokingUserTokenSessionIds([]);
    unlockingUserIdRef.current.clear();
    revokingTokenSessionIdRef.current.clear();
    revokingUserTokenSessionIdRef.current.clear();
  }

  function markIdOperation(
    operationIdsRef: { current: Set<number> },
    setOperationIds: (updater: (current: number[]) => number[]) => void,
    id: number
  ) {
    if (operationIdsRef.current.has(id)) {
      return false;
    }
    operationIdsRef.current.add(id);
    setOperationIds((current) => (current.includes(id) ? current : [...current, id]));
    return true;
  }

  function unmarkIdOperation(
    operationIdsRef: { current: Set<number> },
    setOperationIds: (updater: (current: number[]) => number[]) => void,
    id: number
  ) {
    operationIdsRef.current.delete(id);
    setOperationIds((current) => current.filter((item) => item !== id));
  }

  function updateAuditLogFilters(filters: AuditLogFilters) {
    setAuditLogFilters(filters);
    setAuditLogPage(null);
  }

  async function loadAudits(page = auditLogPage?.page ?? 0) {
    if (!token || !auditReader) {
      return;
    }
    const targetId = auditLogFilters.targetId.trim();
    if (targetId && Number.isNaN(Number(targetId))) {
      setNotice({ tone: 'warn', text: '审计目标 ID 必须是数字' });
      return;
    }
    setAuditLogsLoading(true);
    try {
      const result = await workspaceApi.listAuditLogs(authRequest, token, {
        ...auditLogFilters,
        page,
        size: 8
      });
      setAuditLogPage(result.data);
      setAuditLogs(result.data.items);
      setNotice({ tone: 'ok', text: `审计日志已刷新：共 ${result.data.totalItems} 条` });
    } catch (error) {
      handleRequestError(error);
    } finally {
      setAuditLogsLoading(false);
    }
  }

  async function loadUsers(page = userPage?.page ?? 0) {
    if (!token || !userAdmin) {
      return;
    }
    setUsersLoading(true);
    try {
      const result = await workspaceApi.listUsers(authRequest, token, page);
      setUserPage(result.data);
      setUsers(result.data.items);
      setNotice({ tone: 'ok', text: `用户状态已刷新：共 ${result.data.totalItems} 个账号` });
    } catch (error) {
      handleRequestError(error);
    } finally {
      setUsersLoading(false);
    }
  }

  async function unlockUser(userId: number) {
    if (!token || !userAdmin || !markIdOperation(unlockingUserIdRef, setUnlockingUserIds, userId)) {
      return;
    }
    try {
      const result = await workspaceApi.unlockUser(authRequest, token, userId);
      setUsers((currentUsers) => currentUsers.map((user) => (user.id === result.data.id ? result.data : user)));
      setNotice({ tone: 'ok', text: `账号已解锁：${result.data.username}` });
    } catch (error) {
      handleRequestError(error);
    } finally {
      unmarkIdOperation(unlockingUserIdRef, setUnlockingUserIds, userId);
    }
  }

  async function loadTokenSessions(page = tokenSessionPage?.page ?? 0) {
    if (!token || !tokenSessionAdmin) {
      return;
    }
    setTokenSessionsLoading(true);
    try {
      const result = await workspaceApi.listTokenSessions(authRequest, token, page);
      setTokenSessionPage(result.data);
      setTokenSessions(result.data.items);
      setNotice({ tone: 'ok', text: `Token 会话已刷新：共 ${result.data.totalItems} 条` });
    } catch (error) {
      handleRequestError(error);
    } finally {
      setTokenSessionsLoading(false);
    }
  }

  async function revokeTokenSession(sessionId: number) {
    if (
      !token ||
      !tokenSessionAdmin ||
      !markIdOperation(revokingTokenSessionIdRef, setRevokingTokenSessionIds, sessionId)
    ) {
      return;
    }
    try {
      const result = await workspaceApi.revokeTokenSession(authRequest, token, sessionId);
      setTokenSessions((currentSessions) =>
        currentSessions.map((session) => (session.id === result.data.id ? result.data : session))
      );
      setNotice({ tone: 'ok', text: `Token 会话已吊销：#${result.data.id}` });
    } catch (error) {
      handleRequestError(error);
    } finally {
      unmarkIdOperation(revokingTokenSessionIdRef, setRevokingTokenSessionIds, sessionId);
    }
  }

  async function revokeUserTokenSessions(userId: number) {
    if (
      !token ||
      !tokenSessionAdmin ||
      !markIdOperation(revokingUserTokenSessionIdRef, setRevokingUserTokenSessionIds, userId)
    ) {
      return;
    }
    try {
      const result = await workspaceApi.revokeUserTokenSessions(authRequest, token, userId);
      setTokenSessions((currentSessions) => {
        const updatedById = new Map(result.data.map((session) => [session.id, session]));
        return currentSessions.map((session) => updatedById.get(session.id) || session);
      });
      setNotice({ tone: 'ok', text: `用户 Token 会话已批量吊销：${result.data.length} 条` });
    } catch (error) {
      handleRequestError(error);
    } finally {
      unmarkIdOperation(revokingUserTokenSessionIdRef, setRevokingUserTokenSessionIds, userId);
    }
  }

  return {
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
  };
}
