import { FormEvent } from 'react';
import { KnowledgeBase } from '../api';
import { CardHeading } from '../components/CardHeading';

export function KnowledgeBaseSelectorSection({
  knowledgeBases,
  selectedKnowledgeBaseId,
  knowledgeBaseKeyword,
  canManageKnowledge,
  onSelectKnowledgeBase,
  onKnowledgeBaseKeywordChange,
  onCreateKnowledgeBase
}: {
  knowledgeBases: KnowledgeBase[];
  selectedKnowledgeBaseId: number | '';
  knowledgeBaseKeyword: string;
  canManageKnowledge: boolean;
  onSelectKnowledgeBase: (id: number | '') => void;
  onKnowledgeBaseKeywordChange: (keyword: string) => void;
  onCreateKnowledgeBase: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <article className="card">
      <CardHeading marker="01" title="知识库" />
      <div className="control-row">
        <label htmlFor="knowledgeBaseKeyword">搜索知识库</label>
        <input
          id="knowledgeBaseKeyword"
          value={knowledgeBaseKeyword}
          placeholder="按名称或说明过滤"
          onChange={(event) => onKnowledgeBaseKeywordChange(event.target.value)}
        />
        <p className="hint">
          当前显示 {knowledgeBases.length} 个知识库{knowledgeBaseKeyword.trim() ? '，未命中时可清空关键词' : ''}
        </p>
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
  );
}
