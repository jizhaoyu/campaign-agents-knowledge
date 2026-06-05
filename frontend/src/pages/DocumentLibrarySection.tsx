import { DocumentPage, DocumentUpload } from '../api';
import { CardHeading } from '../components/CardHeading';
import { ListEmpty } from '../components/ListEmpty';
import { PaginationBar } from '../components/PaginationBar';
import { SkeletonBlock } from '../components/SkeletonBlock';

function DocumentRow({
  document,
  onRefreshDocument,
  onReindexDocument,
  onDeleteDocument
}: {
  document: DocumentUpload;
  onRefreshDocument: (documentId: number) => void;
  onReindexDocument: (documentId: number) => void;
  onDeleteDocument: (documentId: number) => void;
}) {
  const documentPending = document.indexStatus === 'PENDING';
  const documentFailed = document.indexStatus === 'FAILED';

  return (
    <div key={document.id} className={`document-row document-status ${document.indexStatus.toLowerCase()}`}>
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
        {documentFailed && <small className="error-text">{document.failureReason || '未知失败原因'}</small>}
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
}

export function DocumentLibrarySection({
  selectedKnowledgeBaseId,
  documents,
  documentPage,
  documentPageSize,
  documentKeyword,
  documentStatusFilter,
  documentsLoading,
  onDocumentKeywordChange,
  onDocumentPageSizeChange,
  onDocumentStatusFilterChange,
  onRefreshDocuments,
  onChangeDocumentPage,
  onRefreshDocument,
  onReindexDocument,
  onRetryFailedDocuments,
  onDeleteDocument
}: {
  selectedKnowledgeBaseId: number | '';
  documents: DocumentUpload[];
  documentPage: DocumentPage | null;
  documentPageSize: number;
  documentKeyword: string;
  documentStatusFilter: string;
  documentsLoading: boolean;
  onDocumentKeywordChange: (keyword: string) => void;
  onDocumentPageSizeChange: (size: number) => void;
  onDocumentStatusFilterChange: (status: string) => void;
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
  const showDocumentSkeleton = documentsLoading && documents.length === 0 && Boolean(selectedKnowledgeBaseId);

  return (
    <article className="card document-library">
      <CardHeading
        marker="03"
        title="文档管理"
        action={
          <button type="button" onClick={onRefreshDocuments} disabled={!selectedKnowledgeBaseId}>
            {documentsLoading ? '刷新中...' : '刷新列表'}
          </button>
        }
      />
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
        <label htmlFor="documentPageSize">每页数量</label>
        <select
          id="documentPageSize"
          value={documentPageSize}
          onChange={(event) => onDocumentPageSizeChange(Number(event.target.value))}
        >
          <option value={5}>5</option>
          <option value={10}>10</option>
          <option value={20}>20</option>
          <option value={50}>50</option>
        </select>
        <button type="button" onClick={onRetryFailedDocuments} disabled={failedCount === 0}>
          重试失败文档
        </button>
      </div>
      {showDocumentSkeleton && <SkeletonBlock label="文档列表骨架" lines={4} variant="panel" />}
      <ListEmpty
        show={documents.length === 0 && !showDocumentSkeleton}
        variant="box"
        title={totalDocumentCount === 0 ? '还没有文档' : '没有匹配文档'}
        text={
          totalDocumentCount === 0
            ? '上传 Markdown / TXT / PDF / DOCX 后，这里会展示解析状态、切片数和失败原因。'
            : '换个关键词或状态筛选试试。'
        }
      />
      {documents.length > 0 && (
        <>
          <div className="document-table" aria-label="文档列表">
            {documents.map((document) => (
              <DocumentRow
                key={document.id}
                document={document}
                onRefreshDocument={onRefreshDocument}
                onReindexDocument={onReindexDocument}
                onDeleteDocument={onDeleteDocument}
              />
            ))}
          </div>
          <PaginationBar
            label="文档分页"
            page={documentPage}
            visibleCount={documents.length}
            itemUnit="个"
            onChangePage={onChangeDocumentPage}
          />
        </>
      )}
    </article>
  );
}
