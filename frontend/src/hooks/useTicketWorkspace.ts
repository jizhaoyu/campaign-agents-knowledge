import { FormEvent, useState } from 'react';
import { AskResult, SimilarTicket, SubmitTicketResult, TicketDraft } from '../api';
import * as workspaceApi from '../services/workspaceApi';
import { WorkspaceApiRequest } from '../services/workspaceApi';

type Notice = {
  tone: 'ok' | 'warn' | 'error';
  text: string;
};

type RequireText = (form: FormData, field: string, label: string) => string | null;

export function useTicketWorkspace({
  token,
  askResult,
  authRequest,
  setNotice,
  handleRequestError,
  requireText
}: {
  token?: string;
  askResult: AskResult | null;
  authRequest: WorkspaceApiRequest;
  setNotice: (notice: Notice) => void;
  handleRequestError: (error: unknown) => void;
  requireText: RequireText;
}) {
  const [ticketDraft, setTicketDraft] = useState<TicketDraft | null>(null);
  const [submitResult, setSubmitResult] = useState<SubmitTicketResult | null>(null);
  const [similarTickets, setSimilarTickets] = useState<SimilarTicket[]>([]);
  const [ticketDraftLoading, setTicketDraftLoading] = useState(false);
  const [ticketSubmitLoading, setTicketSubmitLoading] = useState(false);
  const [similarTicketsLoading, setSimilarTicketsLoading] = useState(false);

  function resetTicketWorkspace() {
    setTicketDraft(null);
    setSubmitResult(null);
    setSimilarTickets([]);
    setTicketDraftLoading(false);
    setTicketSubmitLoading(false);
    setSimilarTicketsLoading(false);
  }

  async function generateTicketDraft() {
    if (!token || !askResult || ticketDraftLoading) {
      return;
    }
    setTicketDraftLoading(true);
    try {
      const result = await workspaceApi.createTicketDraft(authRequest, token, askResult.conversationId);
      setTicketDraft(result.data);
      setNotice({ tone: 'ok', text: '工单草稿已生成' });
    } catch (error) {
      handleRequestError(error);
    } finally {
      setTicketDraftLoading(false);
    }
  }

  async function submitTicket(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (ticketSubmitLoading) {
      return;
    }
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
    setTicketSubmitLoading(true);
    try {
      const result = await workspaceApi.submitTicket(authRequest, token, {
        conversationId: ticketDraft.conversationId,
        title,
        description,
        priority,
        assigneeId
      });
      setSubmitResult(result.data);
      setNotice({ tone: 'ok', text: `工单已提交：${result.data.status}` });
    } catch (error) {
      handleRequestError(error);
    } finally {
      setTicketSubmitLoading(false);
    }
  }

  async function loadSimilarTickets() {
    if (!token || !askResult || similarTicketsLoading) {
      return;
    }
    setSimilarTicketsLoading(true);
    try {
      const result = await workspaceApi.listSimilarTickets(authRequest, token, askResult.conversationId);
      setSimilarTickets(result.data);
      setNotice({ tone: 'ok', text: `找到 ${result.data.length} 条相似工单` });
    } catch (error) {
      handleRequestError(error);
    } finally {
      setSimilarTicketsLoading(false);
    }
  }

  return {
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
  };
}
