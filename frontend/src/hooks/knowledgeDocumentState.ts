import { DocumentPage, DocumentUpload } from '../api';

export type DocumentTransition =
  | {
      tone: 'ok';
      text: string;
    }
  | {
      tone: 'error';
      text: string;
    }
  | null;

export function mergeDocumentsById(
  currentDocuments: DocumentUpload[],
  nextDocuments: DocumentUpload[]
): DocumentUpload[] {
  const documentById = new Map(currentDocuments.map((document) => [document.id, document]));
  nextDocuments.forEach((document) => documentById.set(document.id, document));
  return [...documentById.values()].sort((left, right) => right.id - left.id);
}

export function documentTransitionNotice(
  previousDocuments: DocumentUpload[],
  nextDocuments: DocumentUpload[]
): DocumentTransition {
  const previousStatusById = new Map(previousDocuments.map((document) => [document.id, document.indexStatus]));
  const finishedDocument = nextDocuments.find(
    (document) => previousStatusById.get(document.id) === 'PENDING' && document.indexStatus === 'SUCCESS'
  );
  if (finishedDocument) {
    return {
      tone: 'ok',
      text: `文档已索引：${finishedDocument.fileName}，${finishedDocument.chunkCount} 个切片`
    };
  }
  const failedDocument = nextDocuments.find(
    (document) => previousStatusById.get(document.id) === 'PENDING' && document.indexStatus === 'FAILED'
  );
  if (failedDocument) {
    return {
      tone: 'error',
      text: `文档索引失败：${failedDocument.failureReason || '未知原因'}`
    };
  }
  return null;
}

export function documentStatusNotice(document: DocumentUpload): DocumentTransition {
  if (document.indexStatus === 'SUCCESS') {
    return {
      tone: 'ok',
      text: `文档已索引：${document.fileName}，${document.chunkCount} 个切片`
    };
  }
  if (document.indexStatus === 'FAILED') {
    return {
      tone: 'error',
      text: `文档索引失败：${document.failureReason || '未知原因'}`
    };
  }
  return null;
}

export function normalizeDocumentPage(page: DocumentPage) {
  return page.totalPages > 0 && page.page >= page.totalPages ? page.totalPages - 1 : page.page;
}
