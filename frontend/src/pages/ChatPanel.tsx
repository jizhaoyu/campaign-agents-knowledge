import { FormEvent } from 'react';
import { AskResult, ChatHistoryItem } from '../api';

const demoQuestions = [
  'VPN 无法连接应该怎么处理？',
  '账号被锁定后应该找谁处理？',
  '客户端版本异常会影响登录吗？'
];

export function ChatPanel({
  askResult,
  chatHistory,
  onAsk,
  onRefreshHistory,
  onRestoreHistoryItem
}: {
  askResult: AskResult | null;
  chatHistory: ChatHistoryItem[];
  onAsk: (event: FormEvent<HTMLFormElement>) => void;
  onRefreshHistory: () => void;
  onRestoreHistoryItem: (item: ChatHistoryItem) => void;
}) {
  return (
    <section id="问答" className="section-grid wide-left">
      <article className="card">
        <div className="card-heading">
          <span>03</span>
          <h2>知识问答</h2>
        </div>
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
          <button type="submit" className="primary-action">
            提问
          </button>
        </form>
      </article>

      <article className="card answer-card">
        <div className="card-heading">
          <span>AI</span>
          <h2>回答与引用</h2>
        </div>
        {askResult ? (
          <>
            <p className="answer-text">{askResult.answer}</p>
            <div className="tag-row">
              <span>{askResult.confidence}</span>
              <span>{askResult.fallback ? 'Fallback' : 'Cited'}</span>
              <span>Conversation #{askResult.conversationId}</span>
            </div>
            <div className="citation-list">
              {askResult.citations.map((citation) => (
                <blockquote key={citation.chunkId}>
                  <strong>{citation.documentName}</strong>
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
        <div className="card-heading split-heading">
          <div>
            <span>HIS</span>
            <h2>最近问答</h2>
          </div>
          <button type="button" onClick={onRefreshHistory}>
            刷新历史
          </button>
        </div>
        {chatHistory.length ? (
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
