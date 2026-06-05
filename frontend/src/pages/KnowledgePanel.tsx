import { FormEvent } from 'react';
import { DocumentPage, DocumentUpload, KnowledgeBase } from '../api';
import { DocumentLibrarySection } from './DocumentLibrarySection';
import { DocumentUploadSection } from './DocumentUploadSection';
import { KnowledgeBaseSelectorSection } from './KnowledgeBaseSelectorSection';

export function KnowledgePanel({
  knowledgeBases,
  selectedKnowledgeBaseId,
  knowledgeBaseKeyword,
  documents,
  documentPage,
  documentPageSize,
  documentKeyword,
  documentStatusFilter,
  canManageKnowledge,
  onSelectKnowledgeBase,
  onKnowledgeBaseKeywordChange,
  onDocumentKeywordChange,
  onDocumentPageSizeChange,
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
  knowledgeBaseKeyword: string;
  documents: DocumentUpload[];
  documentPage: DocumentPage | null;
  documentPageSize: number;
  documentKeyword: string;
  documentStatusFilter: string;
  canManageKnowledge: boolean;
  onSelectKnowledgeBase: (id: number | '') => void;
  onKnowledgeBaseKeywordChange: (keyword: string) => void;
  onDocumentKeywordChange: (keyword: string) => void;
  onDocumentPageSizeChange: (size: number) => void;
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
  return (
    <section id="知识库" className="section-grid">
      <KnowledgeBaseSelectorSection
        knowledgeBases={knowledgeBases}
        selectedKnowledgeBaseId={selectedKnowledgeBaseId}
        knowledgeBaseKeyword={knowledgeBaseKeyword}
        canManageKnowledge={canManageKnowledge}
        onSelectKnowledgeBase={onSelectKnowledgeBase}
        onKnowledgeBaseKeywordChange={onKnowledgeBaseKeywordChange}
        onCreateKnowledgeBase={onCreateKnowledgeBase}
      />

      {canManageKnowledge && <DocumentUploadSection onUploadDocument={onUploadDocument} />}

      {canManageKnowledge && (
        <DocumentLibrarySection
          selectedKnowledgeBaseId={selectedKnowledgeBaseId}
          documents={documents}
          documentPage={documentPage}
          documentPageSize={documentPageSize}
          documentKeyword={documentKeyword}
          documentStatusFilter={documentStatusFilter}
          onDocumentKeywordChange={onDocumentKeywordChange}
          onDocumentPageSizeChange={onDocumentPageSizeChange}
          onDocumentStatusFilterChange={onDocumentStatusFilterChange}
          onRefreshDocuments={onRefreshDocuments}
          onChangeDocumentPage={onChangeDocumentPage}
          onRefreshDocument={onRefreshDocument}
          onReindexDocument={onReindexDocument}
          onRetryFailedDocuments={onRetryFailedDocuments}
          onDeleteDocument={onDeleteDocument}
        />
      )}
    </section>
  );
}
