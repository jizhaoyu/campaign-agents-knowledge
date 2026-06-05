import { FormEvent } from 'react';
import { AskResult, SimilarTicket, SubmitTicketResult, TicketDraft } from '../api';
import { CardHeading } from '../components/CardHeading';
import { ListEmpty } from '../components/ListEmpty';

export function TicketPanel({
  askResult,
  ticketDraft,
  submitResult,
  similarTickets,
  onGenerateDraft,
  onLoadSimilarTickets,
  onSubmitTicket,
  onCopyTicketId
}: {
  askResult: AskResult | null;
  ticketDraft: TicketDraft | null;
  submitResult: SubmitTicketResult | null;
  similarTickets: SimilarTicket[];
  onGenerateDraft: () => void;
  onLoadSimilarTickets: () => void;
  onSubmitTicket: (event: FormEvent<HTMLFormElement>) => void;
  onCopyTicketId: (ticketId: number) => void;
}) {
  return (
    <section id="工单" className="section-grid wide-left">
      <article className="card">
        <CardHeading marker="04" title="工单草稿" />
        <div className="button-row">
          <button type="button" onClick={onGenerateDraft} disabled={!askResult}>
            生成草稿
          </button>
          <button type="button" onClick={onLoadSimilarTickets} disabled={!askResult}>
            相似工单
          </button>
        </div>
        {ticketDraft ? (
          <form className="stacked-form" onSubmit={onSubmitTicket}>
            <label htmlFor="ticket-title">标题</label>
            <input id="ticket-title" name="title" defaultValue={ticketDraft.title} required />
            <label htmlFor="ticket-description">描述</label>
            <textarea id="ticket-description" name="description" defaultValue={ticketDraft.description} rows={6} required />
            <label htmlFor="priority">优先级</label>
            <select id="priority" name="priority" defaultValue={ticketDraft.priority} required>
              <option>LOW</option>
              <option>MEDIUM</option>
              <option>HIGH</option>
            </select>
            <label htmlFor="assigneeId">负责人 ID</label>
            <input id="assigneeId" name="assigneeId" defaultValue={ticketDraft.suggestedAssigneeId || ''} />
            <button type="submit" className="primary-action">
              提交工单
            </button>
          </form>
        ) : (
          <p className="muted">先完成一次问答，再生成工单草稿。</p>
        )}
      </article>

      <article className="card">
        <CardHeading marker="SIM" title="相似工单" />
        <ListEmpty show={!similarTickets.length} text="暂无相似工单结果" />
        {similarTickets.map((ticket) => (
          <div className="list-item similar-ticket-item" key={ticket.ticketId}>
            <div>
              <strong>{ticket.title}</strong>
              <span>
                {ticket.priority} / {ticket.status} / score {ticket.score}
              </span>
            </div>
            <p>{ticket.matchSummary || '暂无相似原因'}</p>
            {ticket.matchedKeywords.length > 0 && (
              <div className="tag-row" aria-label={`工单 #${ticket.ticketId} 命中关键词`}>
                {ticket.matchedKeywords.map((keyword) => (
                  <span key={keyword}>{keyword}</span>
                ))}
              </div>
            )}
          </div>
        ))}
        {submitResult && (
          <div className="result-box">
            <div className="result-heading">
              <strong>Ticket #{submitResult.ticketId}</strong>
              <button type="button" onClick={() => onCopyTicketId(submitResult.ticketId)}>
                复制工单号
              </button>
            </div>
            <span>{submitResult.status}</span>
            <small>{submitResult.approvalRequired ? `审批 #${submitResult.approvalTaskId}` : '直接进入 OPEN'}</small>
          </div>
        )}
      </article>
    </section>
  );
}
