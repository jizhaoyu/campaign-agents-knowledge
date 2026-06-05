import { AuditLog, AuditLogFilters, AuditLogPage } from '../api';
import { CardHeading } from '../components/CardHeading';
import { ListEmpty } from '../components/ListEmpty';
import { PaginationBar } from '../components/PaginationBar';
import { SkeletonBlock } from '../components/SkeletonBlock';

export function AuditTimelineSection({
  auditLogs,
  auditLogPage,
  auditLogFilters,
  auditLogsLoading,
  onLoadAudits,
  onAuditLogFiltersChange,
  onCopyTraceId,
  onChangeAuditPage
}: {
  auditLogs: AuditLog[];
  auditLogPage: AuditLogPage | null;
  auditLogFilters: AuditLogFilters;
  auditLogsLoading: boolean;
  onLoadAudits: () => void;
  onAuditLogFiltersChange: (filters: AuditLogFilters) => void;
  onCopyTraceId: (traceId: string) => void;
  onChangeAuditPage: (page: number) => void;
}) {
  function updateAuditFilter(field: keyof AuditLogFilters, value: string) {
    onAuditLogFiltersChange({
      ...auditLogFilters,
      [field]: value
    });
  }

  function compactPayload(payloadJson: string) {
    if (!payloadJson) {
      return '无 payload';
    }
    return payloadJson.length > 180 ? `${payloadJson.slice(0, 180)}...` : payloadJson;
  }

  return (
    <article className="card">
      <CardHeading marker="08" title="审计回看" />
      <div className="audit-filter-panel">
        <label htmlFor="audit-trace-id">traceId</label>
        <input
          id="audit-trace-id"
          value={auditLogFilters.traceId}
          onChange={(event) => updateAuditFilter('traceId', event.target.value)}
          placeholder="按调用链精确查询"
        />
        <label htmlFor="audit-event-type">事件</label>
        <input
          id="audit-event-type"
          value={auditLogFilters.eventType}
          onChange={(event) => updateAuditFilter('eventType', event.target.value)}
          placeholder="如 USER_LOGIN_SUCCEEDED"
        />
        <label htmlFor="audit-target-type">对象</label>
        <input
          id="audit-target-type"
          value={auditLogFilters.targetType}
          onChange={(event) => updateAuditFilter('targetType', event.target.value)}
          placeholder="如 USER / TICKET"
        />
        <label htmlFor="audit-target-id">对象 ID</label>
        <input
          id="audit-target-id"
          value={auditLogFilters.targetId}
          onChange={(event) => updateAuditFilter('targetId', event.target.value)}
          placeholder="数字"
        />
      </div>
      <button type="button" onClick={onLoadAudits}>
        {auditLogsLoading ? '查询中...' : '查询审计'}
      </button>
      <PaginationBar label="审计分页" page={auditLogPage} visibleCount={auditLogs.length} onChangePage={onChangeAuditPage} />
      {auditLogsLoading && !auditLogs.length && <SkeletonBlock label="审计时间线骨架" lines={4} variant="panel" />}
      <ListEmpty show={!auditLogs.length && !auditLogsLoading} text="暂无审计数据" />
      <div className="audit-timeline" aria-label="审计时间线">
        {auditLogs.map((log) => (
          <div className="audit-row" key={log.id}>
            <div>
              <strong>{log.eventType}</strong>
              <span>
                #{log.id} / actor #{log.actorId ?? '-'} / {log.targetType} #{log.targetId}
              </span>
            </div>
            <div className="audit-trace-line">
              <code>{log.traceId}</code>
              <button type="button" onClick={() => onCopyTraceId(log.traceId)}>
                复制 traceId
              </button>
            </div>
            <small>{compactPayload(log.payloadJson)}</small>
          </div>
        ))}
      </div>
    </article>
  );
}
