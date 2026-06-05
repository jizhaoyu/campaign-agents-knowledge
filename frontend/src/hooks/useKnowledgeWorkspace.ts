import { FormEvent, useDeferredValue, useEffect, useRef, useState } from 'react';
import { DocumentPage, DocumentUpload, KnowledgeBase } from '../api';
import {
  documentStatusNotice,
  documentTransitionNotice,
  mergeDocumentsById,
  normalizeDocumentPage
} from './knowledgeDocumentState';
import * as workspaceApi from '../services/workspaceApi';
import { WorkspaceApiRequest } from '../services/workspaceApi';

type Notice = {
  tone: 'ok' | 'warn' | 'error';
  text: string;
};

type RefreshDocumentsOptions = {
  announceTransitions?: boolean;
  announceRefresh?: boolean;
  page?: number;
};

type RefreshKnowledgeBasesOptions = {
  keyword?: string;
  preferredKnowledgeBaseId?: number;
};

const DEFAULT_DOCUMENT_PAGE_SIZE = 10;

export function useKnowledgeWorkspace({
  token,
  knowledgeManager,
  knowledgeRouteActive,
  authRequest,
  setNotice,
  handleRequestError
}: {
  token?: string;
  knowledgeManager: boolean;
  knowledgeRouteActive: boolean;
  authRequest: WorkspaceApiRequest;
  setNotice: (notice: Notice) => void;
  handleRequestError: (error: unknown) => void;
}) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState<number | ''>('');
  const [knowledgeBaseKeyword, setKnowledgeBaseKeyword] = useState('');
  const [documents, setDocuments] = useState<DocumentUpload[]>([]);
  const [documentPage, setDocumentPage] = useState<DocumentPage | null>(null);
  const [documentPageSize, setDocumentPageSize] = useState(DEFAULT_DOCUMENT_PAGE_SIZE);
  const [documentKeyword, setDocumentKeyword] = useState('');
  const [documentStatusFilter, setDocumentStatusFilter] = useState('');
  const [documentsLoading, setDocumentsLoading] = useState(false);
  const deferredKnowledgeBaseKeyword = useDeferredValue(knowledgeBaseKeyword);
  const documentsRef = useRef<DocumentUpload[]>([]);

  function setDocumentList(nextDocuments: DocumentUpload[]) {
    documentsRef.current = nextDocuments;
    setDocuments(nextDocuments);
  }

  function setDocumentPageResult(nextPage: DocumentPage) {
    setDocumentPage(nextPage);
    setDocumentList(nextPage.items);
  }

  function resetKnowledgeWorkspace() {
    setKnowledgeBases([]);
    setSelectedKnowledgeBaseId('');
    setKnowledgeBaseKeyword('');
    setDocumentList([]);
    setDocumentPage(null);
    setDocumentPageSize(DEFAULT_DOCUMENT_PAGE_SIZE);
    setDocumentKeyword('');
    setDocumentStatusFilter('');
    setDocumentsLoading(false);
  }

  function requireText(form: FormData, field: string, label: string) {
    const value = String(form.get(field) || '').trim();
    if (!value) {
      setNotice({ tone: 'warn', text: `请填写${label}` });
      return null;
    }
    return value;
  }

  useEffect(() => {
    if (!token) {
      return;
    }
    void refreshKnowledgeBases(token);
  }, [deferredKnowledgeBaseKeyword, token]);

  useEffect(() => {
    if (!token || !selectedKnowledgeBaseId || !knowledgeManager || !knowledgeRouteActive) {
      setDocumentList([]);
      setDocumentPage(null);
      return;
    }
    void refreshDocuments(selectedKnowledgeBaseId, token);
  }, [documentKeyword, documentPageSize, documentStatusFilter, knowledgeManager, knowledgeRouteActive, selectedKnowledgeBaseId, token]);

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

  async function refreshKnowledgeBases(accessToken = token, options: RefreshKnowledgeBasesOptions = {}) {
    if (!accessToken) {
      return;
    }
    try {
      const keyword = options.keyword ?? deferredKnowledgeBaseKeyword.trim();
      const result = await workspaceApi.listKnowledgeBases(authRequest, accessToken, { keyword });
      setKnowledgeBases(result.data);
      setSelectedKnowledgeBaseId((current) => {
        if (options.preferredKnowledgeBaseId) {
          return options.preferredKnowledgeBaseId;
        }
        if (current && result.data.some((knowledgeBase) => knowledgeBase.id === current)) {
          return current;
        }
        return result.data[0]?.id || '';
      });
    } catch (error) {
      handleRequestError(error);
    }
  }

  function replaceDocument(nextDocument: DocumentUpload) {
    mergeDocuments([nextDocument]);
  }

  function mergeDocuments(nextDocuments: DocumentUpload[]) {
    setDocumentList(mergeDocumentsById(documentsRef.current, nextDocuments));
  }

  function announceDocumentTransitions(previousDocuments: DocumentUpload[], nextDocuments: DocumentUpload[]) {
    const notice = documentTransitionNotice(previousDocuments, nextDocuments);
    if (notice) {
      setNotice(notice);
    }
  }

  async function refreshDocuments(
    knowledgeBaseId = selectedKnowledgeBaseId,
    accessToken = token,
    options: RefreshDocumentsOptions = {}
  ) {
    if (!accessToken || !knowledgeBaseId || !knowledgeManager) {
      return;
    }
    const previousDocuments = documentsRef.current;
    if (!options.announceTransitions) {
      setDocumentsLoading(true);
    }
    try {
      const page = options.page ?? documentPage?.page ?? 0;
      const result = await workspaceApi.listDocuments(authRequest, accessToken, {
        knowledgeBaseId: Number(knowledgeBaseId),
        page,
        size: documentPageSize,
        keyword: documentKeyword.trim(),
        indexStatus: documentStatusFilter
      });
      const normalizedPage = normalizeDocumentPage(result.data);
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
    } finally {
      if (!options.announceTransitions) {
        setDocumentsLoading(false);
      }
    }
  }

  async function refreshDocumentStatus(documentId: number, accessToken = token) {
    if (!accessToken || !knowledgeManager) {
      return;
    }
    try {
      const result = await workspaceApi.getDocument(authRequest, accessToken, documentId);
      replaceDocument(result.data);
      const notice = documentStatusNotice(result.data);
      if (notice) {
        setNotice(notice);
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
      const result = await workspaceApi.reindexDocument(authRequest, token, documentId);
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
      const result = await workspaceApi.retryFailedDocuments(authRequest, token, selectedKnowledgeBaseId);
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
      await workspaceApi.deleteDocument(authRequest, token, documentId);
      await refreshDocuments(selectedKnowledgeBaseId, token);
      setNotice({ tone: 'ok', text: '文档已删除' });
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
      const result = await workspaceApi.createKnowledgeBase(authRequest, token, name, description || null);
      formElement.reset();
      setNotice({ tone: 'ok', text: `知识库已创建：${result.data.name}` });
      setSelectedKnowledgeBaseId(result.data.id);
      setKnowledgeBaseKeyword('');
      setDocumentList([]);
      setDocumentPage(null);
      setDocumentKeyword('');
      setDocumentStatusFilter('');
      await refreshKnowledgeBases(token, { keyword: '', preferredKnowledgeBaseId: result.data.id });
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
      const result = await workspaceApi.uploadDocument(authRequest, token, form);
      formElement.reset();
      replaceDocument(result.data);
      await refreshDocuments(selectedKnowledgeBaseId, token);
      setNotice({ tone: 'ok', text: '文档已上传，正在后台解析和索引' });
    } catch (error) {
      handleRequestError(error);
    }
  }

  return {
    knowledgeBases,
    selectedKnowledgeBaseId,
    knowledgeBaseKeyword,
    documents,
    documentPage,
    documentPageSize,
    documentKeyword,
    documentStatusFilter,
    documentsLoading,
    setSelectedKnowledgeBaseId,
    setKnowledgeBaseKeyword,
    setDocumentKeyword,
    setDocumentPageSize,
    setDocumentStatusFilter,
    setDocumentPage,
    refreshKnowledgeBases,
    refreshDocuments,
    refreshDocumentStatus,
    reindexDocument,
    retryFailedDocuments,
    deleteDocument,
    createKnowledgeBase,
    uploadDocument,
    resetKnowledgeWorkspace
  };
}
