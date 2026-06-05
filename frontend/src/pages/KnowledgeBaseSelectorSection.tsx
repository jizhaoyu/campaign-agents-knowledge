import { FormEvent } from 'react';
import { KnowledgeBase } from '../api';
import { CardHeading } from '../components/CardHeading';

export function KnowledgeBaseSelectorSection({
  knowledgeBases,
  selectedKnowledgeBaseId,
  canManageKnowledge,
  onSelectKnowledgeBase,
  onCreateKnowledgeBase
}: {
  knowledgeBases: KnowledgeBase[];
  selectedKnowledgeBaseId: number | '';
  canManageKnowledge: boolean;
  onSelectKnowledgeBase: (id: number | '') => void;
  onCreateKnowledgeBase: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <article className="card">
      <CardHeading marker="01" title="知识库" />
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
  );
}
