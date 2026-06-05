import { ChangeEvent, FormEvent } from 'react';
import { ApprovalCommentTemplate, ApprovalTask } from '../api';
import { CardHeading } from '../components/CardHeading';
import { ListEmpty } from '../components/ListEmpty';

type ApprovalAction = 'approve' | 'reject';

export function ApprovalQueueSection({
  approvalTasks,
  approvalCommentTemplates,
  onLoadApprovals,
  onDecideApproval
}: {
  approvalTasks: ApprovalTask[];
  approvalCommentTemplates: ApprovalCommentTemplate[];
  onLoadApprovals: () => void;
  onDecideApproval: (id: number, action: ApprovalAction, templateCode: string, comment: string) => void;
}) {
  function decisionFormId(taskId: number, action: ApprovalAction) {
    return `approval-${taskId}-${action}`;
  }

  function templatesFor(action: ApprovalAction) {
    return approvalCommentTemplates.filter((template) => template.action === action);
  }

  function onTemplateChange(event: ChangeEvent<HTMLSelectElement>) {
    const selectedOption = event.currentTarget.selectedOptions.item(0);
    const form = event.currentTarget.form;
    if (!form || !selectedOption) {
      return;
    }
    const textarea = form.elements.namedItem('comment');
    if (textarea instanceof HTMLTextAreaElement) {
      textarea.value = selectedOption.dataset.comment || '';
    }
  }

  function submitDecision(event: FormEvent<HTMLFormElement>, taskId: number, action: ApprovalAction) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const templateCode = String(form.get('templateCode') || '');
    const comment = String(form.get('comment') || '').trim();
    onDecideApproval(taskId, action, templateCode, comment);
  }

  return (
    <article className="card">
      <CardHeading marker="05" title="审批队列" />
      <button type="button" onClick={onLoadApprovals}>
        刷新待审批
      </button>
      <ListEmpty show={!approvalTasks.length} text="暂无待审批任务" />
      {approvalTasks.map((task) => (
        <div className="list-item actionable" key={task.id}>
          <strong>
            #{task.id} {task.targetType}
          </strong>
          <span>
            Target #{task.targetId} / {task.status}
          </span>
          <div className="approval-decision-grid">
            {(['approve', 'reject'] as const).map((action) => (
              <form
                className="approval-decision-form"
                key={action}
                id={decisionFormId(task.id, action)}
                onSubmit={(event) => submitDecision(event, task.id, action)}
              >
                <label htmlFor={`${decisionFormId(task.id, action)}-template`}>
                  {action === 'approve' ? '通过模板' : '驳回模板'}
                </label>
                <select
                  id={`${decisionFormId(task.id, action)}-template`}
                  name="templateCode"
                  defaultValue=""
                  onChange={onTemplateChange}
                >
                  <option value="">自定义备注</option>
                  {templatesFor(action).map((template) => (
                    <option key={template.code} value={template.code} data-comment={template.comment}>
                      {template.label}
                    </option>
                  ))}
                </select>
                <textarea
                  aria-label={action === 'approve' ? `审批 #${task.id} 通过备注` : `审批 #${task.id} 驳回备注`}
                  name="comment"
                  rows={3}
                  maxLength={255}
                  placeholder="可选择模板后再补充说明"
                />
                <button type="submit">{action === 'approve' ? '通过' : '驳回'}</button>
              </form>
            ))}
          </div>
        </div>
      ))}
    </article>
  );
}
