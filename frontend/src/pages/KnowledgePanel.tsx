import { FormEvent } from 'react';
import { DocumentPage, DocumentUpload, KnowledgeBase } from '../api';

export function KnowledgePanel({
  knowledgeBases,
  selectedKnowledgeBaseId,
  documents,
  documentPage,
  documentKeyword,
  documentStatusFilter,
  canManageKnowledge,
  onSelectKnowledgeBase,
  onDocumentKeywordChange,
  onDocumentStatusFilterChange,
  onCreateKnowledgeBase,
  onUploadDocument,
  onRefreshDocuments,
  onChangeDocumentPage,
  onRefreshDocument,
  onReindexDocument,
  onRetryFailedDocuments,
  onDeleteDocument
}: {
  knowledgeBases: KnowledgeBase[];
  selectedKnowledgeBaseId: number | '';
  documents: DocumentUpload[];
  documentPage: DocumentPage | null;
  documentKeyword: string;
  documentStatusFilter: string;
  canManageKnowledge: boolean;
  onSelectKnowledgeBase: (id: number | '') => void;
  onDocumentKeywordChange: (keyword: string) => void;
  onDocumentStatusFilterChange: (status: string) => void;
  onCreateKnowledgeBase: (event: FormEvent<HTMLFormElement>) => void;
  onUploadDocument: (event: FormEvent<HTMLFormElement>) => void;
  onRefreshDocuments: () => void;
  onChangeDocumentPage: (page: number) => void;
  onRefreshDocument: (documentId: number) => void;
  onReindexDocument: (documentId: number) => void;
  onRetryFailedDocuments: () => void;
  onDeleteDocument: (documentId: number) => void;
}) {
  const pendingCount = documents.filter((document) => document.indexStatus === 'PENDING').length;
  const failedCount = documents.filter((document) => document.indexStatus === 'FAILED').length;
  const totalDocumentCount = documentPage?.totalItems ?? documents.length;
  const currentPage = documentPage?.page ?? 0;
  const totalPages = documentPage?.totalPages ?? 0;

  return (
    <section id="知识库" className="section-grid">
      <article className="card">
        <div className="card-heading">
          <span>01</span>
          <h2>知识库</h2>
        </div>
        <div className="control-row">
          <label htmlFor="knowledgeBase">选择知识库</label>
          <select
            id="knowledgeBase"
            value={selectedKnowledgeBaseId}
            onChange={(event) => onSelectKnowledgeBase(event.target.value ? Number(event.target.value) : '')}
          >
            <option value="">请选择</option>
            {knowledgeBases.map((kb) => (
              <option key={kb.id} value={kb.id}>
                #{kb.id} {kb.name}
              </option>
            ))}
          </select>
        </div>
        {canManageKnowledge ? (
          <form className="stacked-form" onSubmit={onCreateKnowledgeBase}>
            <label htmlFor="kb-name">新建知识库</label>
            <input id="kb-name" name="name" placeholder="IT Support KB" required />
            <input name="description" placeholder="知识库说明" />
            <button type="submit">创建知识库</button>
          </form>
        ) : (
          <p className="hint">当前角色可选择已有知识库进行问答和工单协同，文档管理入口仅管理员可见。</p>
        )}
      </article>

      {canManageKnowledge && (
        <article className="card">
          <div className="card-heading">
            <span>02</span>
            <h2>文档上传</h2>
          </div>
          <form className="stacked-form" onSubmit={onUploadDocument}>
            <label htmlFor="file">上传 Markdown / TXT / PDF / DOCX</label>
            <input id="file" name="file" type="file" required />
            <button type="submit">上传并后台索引</button>
          </form>
          <div className="result-box">
            <strong>处理策略</strong>
            <span>上传后立即进入后台队列，成功时替换切片；重建失败时保留旧切片。</span>
          </div>
        </article>
      )}

      {canManageKnowledge && (
        <article className="card document-library">
          <div className="card-heading split-heading">
            <div>
              <span>03</span>
              <h2>文档管理</h2>
            </div>
            <button type="button" onClick={onRefreshDocuments} disabled={!selectedKnowledgeBaseId}>
              刷新列表
            </button>
          </div>
          <p className="hint compact">
            当前筛选共 {totalDocumentCount} 个文档，本页 {documents.length} 个{pendingCount ? `，${pendingCount} 个正在索引` : ''}
            {failedCount ? `，${failedCount} 个失败` : ''}
          </p>
          <div className="document-toolbar">
            <label htmlFor="documentKeyword">搜索文档</label>
            <input
              id="documentKeyword"
              value={documentKeyword}
              onChange={(event) => onDocumentKeywordChange(event.target.value)}
              placeholder="按文件名服务端搜索"
            />
            <label htmlFor="documentStatus">索引状态</label>
            <select
              id="documentStatus"
              value={documentStatusFilter}
              onChange={(event) => onDocumentStatusFilterChange(event.target.value)}
            >
              <option value="">全部状态</option>
              <option value="PENDING">PENDING</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILED">FAILED</option>
            </select>
            <button type="button" onClick={onRetryFailedDocuments} disabled={failedCount === 0}>
              重试失败文档
            </button>
          </div>
          {documents.length === 0 ? (
            <div className="result-box">
              <strong>{totalDocumentCount === 0 ? '还没有文档' : '没有匹配文档'}</strong>
              <span>
                {totalDocumentCount === 0
                  ? '上传 Markdown / TXT / PDF / DOCX 后，这里会展示解析状态、切片数和失败原因。'
                  : '换个关键词或状态筛选试试。'}
              </span>
            </div>
          ) : (
            <>
              <div className="document-table" aria-label="文档列表">
                {documents.map((document) => {
                  const documentPending = document.indexStatus === 'PENDING';
                  const documentFailed = document.indexStatus === 'FAILED';
                  return (
                    <div
                      key={document.id}
                      className={`document-row document-status ${document.indexStatus.toLowerCase()}`}
                    >
                      <div>
                        <strong>{document.fileName}</strong>
                        <small>#{document.id}</small>
                      </div>
                      <div>
                        <span>
                          解析 {document.parseStatus} / 索引 {document.indexStatus}
                        </span>
                        <small>{document.chunkCount} chunks</small>
                      </div>
                      <div>
                        {documentPending && <small>后台处理中，列表会自动刷新。</small>}
                        {documentFailed && (
                          <small className="error-text">{document.failureReason || '未知失败原因'}</small>
                        )}
                        {!documentPending && !documentFailed && <small>可用于问答检索。</small>}
                      </div>
                      <div className="row-actions">
                        <button type="button" onClick={() => onRefreshDocument(document.id)}>
                          状态
                        </button>
                        <button type="button" onClick={() => onReindexDocument(document.id)} disabled={documentPending}>
                          重建索引
                        </button>
                        <button
                          className="danger-action"
                          type="button"
                          onClick={() => onDeleteDocument(document.id)}
                          disabled={documentPending}
                        >
                          删除
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
              <div className="pagination-bar" aria-label="文档分页">
                <span>
                  第 {totalPages === 0 ? 0 : currentPage + 1} / {totalPages} 页，共 {totalDocumentCount} 个
                </span>
                <div className="button-row">
                  <button
                    type="button"
                    onClick={() => onChangeDocumentPage(currentPage - 1)}
                    disabled={!documentPage?.hasPrevious}
                  >
                    上一页
                  </button>
                  <button
                    type="button"
                    onClick={() => onChangeDocumentPage(currentPage + 1)}
                    disabled={!documentPage?.hasNext}
                  >
                    下一页
                  </button>
                </div>
              </div>
            </>
          )}
        </article>
      )}
    </section>
  );
}
