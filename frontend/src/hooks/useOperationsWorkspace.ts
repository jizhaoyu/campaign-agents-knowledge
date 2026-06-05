import { useEffect, useRef, useState } from 'react';
import { AiRuntimeStatus, isApiError, OperationsDashboard } from '../api';
import * as workspaceApi from '../services/workspaceApi';
import { WorkspaceApiRequest } from '../services/workspaceApi';

type Notice = {
  tone: 'ok' | 'warn' | 'error';
  text: string;
};

export function useOperationsWorkspace({
  token,
  routeId,
  authRequest,
  dashboardReader,
  setNotice,
  handleRequestError
}: {
  token?: string;
  routeId: string;
  authRequest: WorkspaceApiRequest;
  dashboardReader: boolean;
  setNotice: (notice: Notice) => void;
  handleRequestError: (error: unknown) => void;
}) {
  const [operationsDashboard, setOperationsDashboard] = useState<OperationsDashboard | null>(null);
  const [aiRuntimeStatus, setAiRuntimeStatus] = useState<AiRuntimeStatus | null>(null);
  const [operationsDashboardLoading, setOperationsDashboardLoading] = useState(false);
  const [aiRuntimeStatusLoading, setAiRuntimeStatusLoading] = useState(false);
  const [operationsDashboardError, setOperationsDashboardError] = useState<string | null>(null);
  const [aiRuntimeStatusError, setAiRuntimeStatusError] = useState<string | null>(null);
  const operationsDashboardInFlightRef = useRef(false);
  const aiRuntimeStatusInFlightRef = useRef(false);

  useEffect(() => {
    if (!token || !dashboardReader) {
      setOperationsDashboard(null);
      return;
    }
    void refreshOperationsDashboard(token);
  }, [dashboardReader, token]);

  useEffect(() => {
    if (!token || !dashboardReader || routeId !== 'ai-config') {
      setAiRuntimeStatus(null);
      return;
    }
    void refreshAiRuntimeStatus(token);
  }, [dashboardReader, routeId, token]);

  function resetOperationsWorkspace() {
    setOperationsDashboard(null);
    setAiRuntimeStatus(null);
    setOperationsDashboardLoading(false);
    setAiRuntimeStatusLoading(false);
    setOperationsDashboardError(null);
    setAiRuntimeStatusError(null);
    operationsDashboardInFlightRef.current = false;
    aiRuntimeStatusInFlightRef.current = false;
  }

  async function refreshOperationsDashboard(
    accessToken = token,
    options: { announceRefresh?: boolean } = {}
  ) {
    if (!accessToken || !dashboardReader || operationsDashboardInFlightRef.current) {
      return;
    }
    operationsDashboardInFlightRef.current = true;
    setOperationsDashboardLoading(true);
    try {
      const result = await workspaceApi.getOperationsDashboard(authRequest, accessToken);
      setOperationsDashboard(result.data);
      setOperationsDashboardError(null);
      if (options.announceRefresh) {
        setNotice({ tone: 'ok', text: '运营指标已刷新' });
      }
    } catch (error) {
      setOperationsDashboardError(formatRecoverableError(error));
      handleRequestError(error);
    } finally {
      operationsDashboardInFlightRef.current = false;
      setOperationsDashboardLoading(false);
    }
  }

  async function refreshAiRuntimeStatus(accessToken = token, options: { announceRefresh?: boolean } = {}) {
    if (!accessToken || !dashboardReader || aiRuntimeStatusInFlightRef.current) {
      return;
    }
    aiRuntimeStatusInFlightRef.current = true;
    setAiRuntimeStatusLoading(true);
    try {
      const result = await workspaceApi.getAiRuntimeStatus(authRequest, accessToken);
      setAiRuntimeStatus(result.data);
      setAiRuntimeStatusError(null);
      if (options.announceRefresh) {
        setNotice({ tone: 'ok', text: 'AI 运行配置已刷新' });
      }
    } catch (error) {
      setAiRuntimeStatusError(formatRecoverableError(error));
      handleRequestError(error);
    } finally {
      aiRuntimeStatusInFlightRef.current = false;
      setAiRuntimeStatusLoading(false);
    }
  }

  return {
    operationsDashboard,
    aiRuntimeStatus,
    operationsDashboardLoading,
    aiRuntimeStatusLoading,
    operationsDashboardError,
    aiRuntimeStatusError,
    refreshOperationsDashboard,
    refreshAiRuntimeStatus,
    resetOperationsWorkspace
  };
}

function formatRecoverableError(error: unknown) {
  if (isApiError(error)) {
    const prefix =
      error.kind === 'validation'
        ? '提交内容无效'
        : error.kind === 'server'
          ? '后端异常'
          : error.kind === 'network'
            ? '网络请求失败'
            : error.kind === 'auth'
              ? '登录已失效'
              : '业务处理失败';
    const traceId = error.traceId ? `，traceId: ${error.traceId}` : '';
    return `${prefix}：${error.message}${traceId}`;
  }
  return error instanceof Error ? error.message : '未知错误';
}
