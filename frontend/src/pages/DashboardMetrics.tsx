import { AskResult, KnowledgeBase, OperationsDashboard, SubmitTicketResult } from '../api';
import { Metric } from '../components/Metric';

export function DashboardMetrics({
  knowledgeBases,
  askResult,
  submitResult,
  operationsDashboard,
  canReadDashboard,
  onRefreshOperationsDashboard
}: {
  knowledgeBases: KnowledgeBase[];
  askResult: AskResult | null;
  submitResult: SubmitTicketResult | null;
  operationsDashboard: OperationsDashboard | null;
  canReadDashboard: boolean;
  onRefreshOperationsDashboard: () => void;
}) {
  const knowledgeBaseCount = operationsDashboard?.knowledgeBaseCount ?? knowledgeBases.length;
  const generatedAt = operationsDashboard?.generatedAt
    ? new Date(operationsDashboard.generatedAt).toLocaleString()
    : '尚未加载';
  const healthLabelByLevel = {
    HEALTHY: '健康',
    ATTENTION: '关注',
    CRITICAL: '紧急'
  } as const;
  const healthLevel = operationsDashboard?.healthLevel ?? 'HEALTHY';

  return (
    <section id="总览" className="metric-section">
      <div className="metric-grid">
        <Metric title="知识库" value={knowledgeBaseCount} caption="当前业务知识空间" />
        <Metric title="最近会话" value={askResult?.conversationId || '-'} caption="问答生成的 conversationId" />
        <Metric title="工单状态" value={submitResult?.status || '未提交'} caption="高优先级会进入审批" />
        <Metric title="AI 模式" value="OpenAI-compatible" caption="支持中转站 gpt-5.4" />
        {operationsDashboard && (
          <>
            <Metric
              title="索引队列"
              value={operationsDashboard.pendingIndexTaskCount + operationsDashboard.runningIndexTaskCount}
              caption={`等待 ${operationsDashboard.pendingIndexTaskCount} / 运行 ${operationsDashboard.runningIndexTaskCount}`}
            />
            <Metric
              title="索引失败"
              value={operationsDashboard.failedDocumentCount}
              caption={`失败任务 ${operationsDashboard.failedIndexTaskCount} / 文档 ${operationsDashboard.documentCount}`}
            />
            <Metric title="待审批" value={operationsDashboard.pendingApprovalCount} caption="全局待处理审批任务" />
            <Metric
              title="运营健康"
              value={healthLabelByLevel[healthLevel]}
              caption={`${operationsDashboard.alertCount} 个待处理提醒`}
            />
            <Metric
              title="高风险工单"
              value={operationsDashboard.activeHighRiskTicketCount}
              caption={`待审批 ${operationsDashboard.pendingHighRiskTicketCount} / 已开放或待批`}
            />
            <Metric title="活跃会话" value={operationsDashboard.activeTokenSessionCount} caption="未吊销且未过期 Token 会话" />
          </>
        )}
      </div>
      {operationsDashboard && (
        <div className={`health-panel ${operationsDashboard.healthLevel.toLowerCase()}`}>
          <div>
            <strong>运营摘要：{operationsDashboard.healthSummary}</strong>
            <span>建议动作</span>
          </div>
          <ul>
            {operationsDashboard.recommendedActions.map((action) => (
              <li key={action}>{action}</li>
            ))}
          </ul>
        </div>
      )}
      {canReadDashboard && (
        <div className="metric-toolbar">
          <span>运营指标更新时间：{generatedAt}</span>
          <button type="button" onClick={onRefreshOperationsDashboard}>
            刷新运营指标
          </button>
        </div>
      )}
    </section>
  );
}
