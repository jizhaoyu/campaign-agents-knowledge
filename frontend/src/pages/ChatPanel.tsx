import { FormEvent } from 'react';
import { AskResult, ChatHistoryItem, Citation } from '../api';
import { CardHeading } from '../components/CardHeading';
import { SkeletonBlock } from '../components/SkeletonBlock';

const demoQuestions = [
  'VPN 无法连接应该怎么处理？',
  '账号被锁定后应该找谁处理？',
  '客户端版本异常会影响登录吗？'
];

export function ChatPanel({
  askResult,
  chatHistory,
  asking,
  chatHistoryLoading,
  onAsk,
  onRefreshHistory,
  onRestoreHistoryItem,
  onCopyCitation
}: {
  askResult: AskResult | null;
  chatHistory: ChatHistoryItem[];
  asking: boolean;
  chatHistoryLoading: boolean;
  onAsk: (event: FormEvent<HTMLFormElement>) => void;
  onRefreshHistory: () => void;
  onRestoreHistoryItem: (item: ChatHistoryItem) => void;
  onCopyCitation: (citation: Citation) => void;
}) {
  return (
    <section id="问答" className="section-grid wide-left">
      <article className="card">
        <CardHeading marker="03" title="知识问答" />
        <form className="stacked-form" onSubmit={onAsk}>
          <label htmlFor="question">问题</label>
          <textarea id="question" name="question" defaultValue={demoQuestions[0]} rows={4} required />
          <div className="quick-row">
            {demoQuestions.map((question) => (
              <button
                key={question}
                type="button"
                onClick={(event) => {
                  const form = event.currentTarget.closest('form');
                  const input = form?.querySelector<HTMLTextAreaElement>('#question');
                  if (input) input.value = question;
                }}
              >
                {question}
              </button>
            ))}
          </div>
          <button type="submit" className="primary-action" disabled={asking}>
            {asking ? '生成中...' : '提问'}
          </button>
        </form>
      </article>

      <article className="card answer-card">
        <CardHeading marker="AI" title="回答与引用" />
        {asking && !askResult ? (
          <SkeletonBlock label="回答生成骨架" lines={4} variant="panel" />
        ) : askResult ? (
          <>
            <p className="answer-text">{askResult.answer}</p>
            <div className="tag-row">
              <span>{askResult.confidence}</span>
              <span>{askResult.fallback ? 'Fallback' : 'Cited'}</span>
              <span>Conversation #{askResult.conversationId}</span>
            </div>
            <div className="citation-list">
              {askResult.citations.map((citation) => (
                <blockquote className="citation-card" key={citation.chunkId}>
                  <div>
                    <strong>{citation.documentName}</strong>
                    <button type="button" onClick={() => onCopyCitation(citation)}>
                      复制引用
                    </button>
                  </div>
                  <p>{citation.snippet}</p>
                </blockquote>
              ))}
            </div>
          </>
        ) : (
          <p className="muted">完成一次提问后，这里会显示模型回答、引用片段和置信度。</p>
        )}
      </article>

      <article className="card">
        <CardHeading
          marker="HIS"
          title="最近问答"
          action={
            <button type="button" onClick={onRefreshHistory} disabled={chatHistoryLoading}>
              {chatHistoryLoading ? '刷新中...' : '刷新历史'}
            </button>
          }
        />
        {chatHistoryLoading && !chatHistory.length ? (
          <SkeletonBlock label="问答历史骨架" lines={3} variant="panel" />
        ) : chatHistory.length ? (
          <div className="history-list">
            {chatHistory.map((item) => (
              <button
                type="button"
                className="history-item"
                key={item.conversationId}
                onClick={() => onRestoreHistoryItem(item)}
              >
                <strong>{item.question}</strong>
                <span>
                  Conversation #{item.conversationId} / {item.confidence} / {item.fallback ? 'Fallback' : 'Cited'}
                </span>
                <small>{item.createdAt}</small>
              </button>
            ))}
          </div>
        ) : (
          <p className="muted">暂无历史问答。完成一次提问后，这里会保留最近会话。</p>
        )}
      </article>
    </section>
  );
}
