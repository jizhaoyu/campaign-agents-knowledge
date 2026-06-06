import { FormEvent } from 'react';
import { CardHeading } from '../components/CardHeading';

export function DocumentUploadSection({
  uploadDocumentLoading,
  onUploadDocument
}: {
  uploadDocumentLoading: boolean;
  onUploadDocument: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <article className="card">
      <CardHeading marker="02" title="文档上传" />
      <form className="stacked-form" onSubmit={onUploadDocument}>
        <label htmlFor="file">上传 Markdown / TXT / PDF / DOCX</label>
        <input id="file" name="file" type="file" required disabled={uploadDocumentLoading} />
        <button type="submit" disabled={uploadDocumentLoading}>
          {uploadDocumentLoading ? '上传中...' : '上传并后台索引'}
        </button>
      </form>
      <div className="result-box">
        <strong>处理策略</strong>
        <span>上传后立即进入后台队列，成功时替换切片；重建失败时保留旧切片。</span>
      </div>
    </article>
  );
}
