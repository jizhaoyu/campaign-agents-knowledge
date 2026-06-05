import { useEffect, useRef, useState } from 'react';
import { AiRuntimeStatus, OperationsDashboard } from '../api';
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
      if (options.announceRefresh) {
        setNotice({ tone: 'ok', text: '运营指标已刷新' });
      }
    } catch (error) {
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
      if (options.announceRefresh) {
        setNotice({ tone: 'ok', text: 'AI 运行配置已刷新' });
      }
    } catch (error) {
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
    refreshOperationsDashboard,
    refreshAiRuntimeStatus,
    resetOperationsWorkspace
  };
}
