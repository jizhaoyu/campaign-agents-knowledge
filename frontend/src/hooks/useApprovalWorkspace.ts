import { useState } from 'react';
import { ApprovalCommentTemplate, ApprovalTask } from '../api';
import * as workspaceApi from '../services/workspaceApi';
import { WorkspaceApiRequest } from '../services/workspaceApi';

type Notice = {
  tone: 'ok' | 'warn' | 'error';
  text: string;
};

export function useApprovalWorkspace({
  token,
  authRequest,
  approvalReviewer,
  setNotice,
  handleRequestError
}: {
  token?: string;
  authRequest: WorkspaceApiRequest;
  approvalReviewer: boolean;
  setNotice: (notice: Notice) => void;
  handleRequestError: (error: unknown) => void;
}) {
  const [approvalTasks, setApprovalTasks] = useState<ApprovalTask[]>([]);
  const [approvalCommentTemplates, setApprovalCommentTemplates] = useState<ApprovalCommentTemplate[]>([]);
  const [approvalsLoading, setApprovalsLoading] = useState(false);

  function resetApprovalWorkspace() {
    setApprovalTasks([]);
    setApprovalCommentTemplates([]);
    setApprovalsLoading(false);
  }

  async function loadApprovals() {
    if (!token || !approvalReviewer) {
      return;
    }
    setApprovalsLoading(true);
    try {
      const result = await workspaceApi.loadApprovalWorkspace(authRequest, token);
      setApprovalTasks(result.tasks);
      setApprovalCommentTemplates(result.templates);
      setNotice({ tone: 'ok', text: `待审批 ${result.tasks.length} 条` });
    } catch (error) {
      handleRequestError(error);
    } finally {
      setApprovalsLoading(false);
    }
  }

  async function decideApproval(id: number, action: 'approve' | 'reject', templateCode: string, comment: string) {
    if (!token || !approvalReviewer) {
      return;
    }
    try {
      await workspaceApi.decideApproval(authRequest, token, id, action, {
        templateCode: templateCode || null,
        comment
      });
      await loadApprovals();
    } catch (error) {
      handleRequestError(error);
    }
  }

  return {
    approvalTasks,
    approvalCommentTemplates,
    approvalsLoading,
    loadApprovals,
    decideApproval,
    resetApprovalWorkspace
  };
}
