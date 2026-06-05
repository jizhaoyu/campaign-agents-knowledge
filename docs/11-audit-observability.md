# 审计与可观测性

## 文档索引任务

文档上传、重建索引和失败重试都会写入 `document_index_task`。该表用于观测后台索引任务，而不是只依赖内存线程状态。

关键字段：

- `status`：PENDING/RUNNING/SUCCESS/FAILED
- `attempt_count`：当前尝试次数
- `max_attempts`：最大尝试次数
- `last_error`：最近一次失败原因
- `next_run_at`：下一次可领取时间
- `started_at` / `finished_at`：执行时间窗口

worker 会周期性扫描到期任务，并把超过 `app.document-index.stale-timeout-seconds` 的 RUNNING 任务重新入队。业务类失败（例如暂不支持的文件类型）会直接失败；系统类异常会按 `app.document-index.max-attempts` 自动重试。

## 目标

- 能回看系统做了什么
- 能定位失败发生在哪一步
- 能量化系统表现

## 审计事件

第一版至少记录以下事件：

- USER_LOGIN_SUCCEEDED
- USER_LOGIN_FAILED
- USER_LOGIN_LOCKED
- USER_TOKEN_REFRESHED
- USER_TOKEN_REFRESH_FAILED
- USER_LOGOUT
- KB_CREATED
- DOCUMENT_UPLOADED
- DOCUMENT_PARSED
- DOCUMENT_INDEXED
- QUESTION_ASKED
- CITATION_SELECTED
- ANSWER_RETURNED
- TOOL_CALLED
- TICKET_DRAFT_GENERATED
- APPROVAL_CREATED
- APPROVAL_APPROVED
- APPROVAL_REJECTED
- USER_UNLOCKED
- TOKEN_SESSION_REVOKED
- USER_TOKEN_SESSIONS_REVOKED

## 审计字段

每条事件尽量包含：

- traceId
- actorId
- eventType
- targetType
- targetId
- payloadJson
- createdAt

## 指标设计

当前已提供 `GET /api/v1/dashboard/operations` 作为第一版只读运营汇总接口，并在前端总览页展示：

- 知识库总数、文档总数
- `document_index_task` 中 PENDING/RUNNING/FAILED 任务数量
- `document_record.index_status=FAILED` 的失败文档数量
- `approval_task.status=PENDING` 的待审批数量
- HIGH 优先级且处于 `PENDING_APPROVAL` 或 `OPEN` 的高风险工单数量
- 未吊销且未过期的 token 会话数量
- 运营健康等级 `HEALTHY` / `ATTENTION` / `CRITICAL`
- 运营告警数量、健康摘要和建议动作列表

这版指标来自业务表实时 count，并由后端根据失败索引、队列堆积、待审批和高风险工单生成建议动作，适合小规模管理台总览；后续如需商业化规模下的趋势、分位耗时和外部告警，应接入 Prometheus/Grafana 或独立指标存储，避免首页请求承担重统计。

### 问答指标

- 问答总次数
- 平均响应时间
- 95 分位响应时间
- 拒答次数

### 检索指标

- 检索命中率
- 引用返回率

### 工具指标

- 每种工具调用次数
- 每种工具成功率
- 每种工具平均耗时

### 审批指标

- 待审批数量
- 审批通过率
- 审批平均耗时

## 日志建议

- 应用日志和审计日志分离
- 关键调用链统一 traceId
- 错误日志保留最小必要上下文

## 第一版可视化建议

- 管理后台简单列表页
- 或接入 Prometheus + Grafana

第一版只要能查和看，不必追求高级图表。
