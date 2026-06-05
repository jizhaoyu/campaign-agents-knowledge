import { AiRuntimeComponent, AiRuntimeStatus } from '../api';
import { CardHeading } from '../components/CardHeading';
import { InlineRetry } from '../components/InlineRetry';
import { ListEmpty } from '../components/ListEmpty';
import { SkeletonBlock } from '../components/SkeletonBlock';

function availabilityLabel(value: boolean) {
  return value ? '就绪' : '缺失';
}

function enabledLabel(value: boolean) {
  return value ? '启用' : '关闭';
}

function safeValue(value: string | null) {
  return value || '-';
}

function RuntimeComponentCard({
  title,
  status
}: {
  title: string;
  status: AiRuntimeComponent;
}) {
  return (
    <div className={`ai-runtime-card ${status.enabled ? 'enabled' : 'disabled'}`}>
      <div>
        <strong>{title}</strong>
        <span>{enabledLabel(status.enabled)}</span>
      </div>
      <dl>
        <div>
          <dt>Provider</dt>
          <dd>{safeValue(status.provider)}</dd>
        </div>
        <div>
          <dt>Model</dt>
          <dd>{safeValue(status.model)}</dd>
        </div>
        <div>
          <dt>Base URL</dt>
          <dd>{safeValue(status.baseUrl)}</dd>
        </div>
        <div>
          <dt>Path</dt>
          <dd>{safeValue(status.path)}</dd>
        </div>
        <div>
          <dt>凭证</dt>
          <dd>{availabilityLabel(status.credentialConfigured)}</dd>
        </div>
        <div>
          <dt>模型 Bean</dt>
          <dd>{availabilityLabel(status.modelAvailable)}</dd>
        </div>
      </dl>
    </div>
  );
}

export function AiRuntimePanel({
  aiRuntimeStatus,
  aiRuntimeStatusLoading,
  aiRuntimeStatusError,
  canReadDashboard,
  onRefresh
}: {
  aiRuntimeStatus: AiRuntimeStatus | null;
  aiRuntimeStatusLoading: boolean;
  aiRuntimeStatusError: string | null;
  canReadDashboard: boolean;
  onRefresh: () => void;
}) {
  if (!canReadDashboard) {
    return (
      <section className="section-grid">
        <article className="card">
          <CardHeading marker="09" title="AI 配置" />
          <p className="hint">当前账号没有读取运行配置的权限。</p>
        </article>
      </section>
    );
  }

  const generatedAt = aiRuntimeStatus?.generatedAt
    ? new Date(aiRuntimeStatus.generatedAt).toLocaleString()
    : '尚未加载';
  const readinessText = {
    READY: '可用',
    PARTIAL: '部分可用',
    DISABLED: '已关闭'
  } as const;
  const readinessLevel = aiRuntimeStatus?.readinessLevel ?? 'DISABLED';
  const showAiRuntimeSkeleton = aiRuntimeStatusLoading && !aiRuntimeStatus && !aiRuntimeStatusError;

  return (
    <section className="section-grid ai-runtime-section">
      <article className="card ai-runtime-overview">
        <CardHeading
          marker="09"
          title="AI 运行配置"
          action={
            <button type="button" onClick={onRefresh} disabled={aiRuntimeStatusLoading}>
              {aiRuntimeStatusLoading ? '刷新中...' : '刷新配置'}
            </button>
          }
        />
        {showAiRuntimeSkeleton ? (
          <SkeletonBlock label="AI 运行配置概览骨架" lines={3} variant="panel" />
        ) : (
          <>
            <div className={`ai-readiness ${readinessLevel.toLowerCase()}`}>
              <strong>{readinessText[readinessLevel]}</strong>
              <span>不展示 API key，只展示是否已配置凭证。</span>
            </div>
            <p className="hint compact">更新时间：{generatedAt}</p>
            <div className="tag-row" aria-label="AI profile">
              {(aiRuntimeStatus?.activeProfiles.length ? aiRuntimeStatus.activeProfiles : ['default']).map((profile) => (
                <span key={profile}>{profile}</span>
              ))}
            </div>
          </>
        )}
      </article>

      <article className="card ai-runtime-guidance">
        <CardHeading marker="10" title="启动检查" />
        {aiRuntimeStatusError && (
          <InlineRetry
            title={aiRuntimeStatus ? 'AI 运行配置刷新失败' : 'AI 运行配置加载失败'}
            message={aiRuntimeStatusError}
            actionLabel="重试配置读取"
            onRetry={onRefresh}
            loading={aiRuntimeStatusLoading}
          />
        )}
        {showAiRuntimeSkeleton && <SkeletonBlock label="AI 启动检查骨架" lines={2} variant="panel" />}
        <ListEmpty
          show={!aiRuntimeStatus && !aiRuntimeStatusError && !showAiRuntimeSkeleton}
          text={aiRuntimeStatusLoading ? '正在读取 AI 运行状态...' : '点击刷新配置加载 AI 运行状态'}
        />
        {aiRuntimeStatus && (
          <ul>
            {aiRuntimeStatus.warnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
        )}
      </article>

      {showAiRuntimeSkeleton && (
        <article className="card ai-runtime-details">
          <CardHeading marker="11" title="组件状态" />
          <SkeletonBlock label="AI 组件状态骨架" lines={4} variant="panel" />
        </article>
      )}
      {aiRuntimeStatus && (
        <article className="card ai-runtime-details">
          <CardHeading marker="11" title="组件状态" />
          <div className="ai-runtime-grid">
            <RuntimeComponentCard title="Chat Completions" status={aiRuntimeStatus.chat} />
            <RuntimeComponentCard title="Embeddings" status={aiRuntimeStatus.embedding} />
          </div>
        </article>
      )}
    </section>
  );
}
