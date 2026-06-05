import { FormEvent, useState } from 'react';
import { AskResult, ChatHistoryItem } from '../api';
import * as workspaceApi from '../services/workspaceApi';
import { WorkspaceApiRequest } from '../services/workspaceApi';

type Notice = {
  tone: 'ok' | 'warn' | 'error';
  text: string;
};

type RequireText = (form: FormData, field: string, label: string) => string | null;

export function useChatWorkspace({
  token,
  selectedKnowledgeBaseId,
  authRequest,
  setSelectedKnowledgeBaseId,
  clearTicketState,
  setNotice,
  handleRequestError,
  requireText
}: {
  token?: string;
  selectedKnowledgeBaseId: number | '';
  authRequest: WorkspaceApiRequest;
  setSelectedKnowledgeBaseId: (id: number | '') => void;
  clearTicketState: () => void;
  setNotice: (notice: Notice) => void;
  handleRequestError: (error: unknown) => void;
  requireText: RequireText;
}) {
  const [askResult, setAskResult] = useState<AskResult | null>(null);
  const [chatHistory, setChatHistory] = useState<ChatHistoryItem[]>([]);

  function resetChatWorkspace() {
    setAskResult(null);
    setChatHistory([]);
  }

  async function refreshChatHistory(accessToken = token) {
    if (!accessToken) {
      return;
    }
    try {
      const result = await workspaceApi.listChatHistory(authRequest, accessToken);
      setChatHistory(result.data);
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
      const result = await workspaceApi.askKnowledgeBase(authRequest, token, Number(selectedKnowledgeBaseId), question);
      setAskResult(result.data);
      await refreshChatHistory(token);
      clearTicketState();
      setNotice({ tone: 'ok', text: `已生成回答，置信度 ${result.data.confidence}` });
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
    clearTicketState();
    setNotice({ tone: 'ok', text: `已恢复会话 #${item.conversationId}` });
  }

  return {
    askResult,
    chatHistory,
    refreshChatHistory,
    ask,
    restoreChatHistoryItem,
    resetChatWorkspace
  };
}
