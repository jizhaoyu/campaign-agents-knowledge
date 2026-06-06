# API 规范

## 通用约定

- 协议：HTTP/JSON
- 前缀：`/api/v1`
- 认证：Bearer Token；客户端 token 为 opaque token，服务端通过持久化会话解析
- 时间格式：ISO-8601

## 统一响应结构

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "abc123"
}
```

## 认证接口

### POST /api/v1/auth/login

请求：

```json
{
  "username": "admin",
  "password": "******"
}
```

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "accessToken": "token",
    "refreshToken": "refresh-token",
    "expiresIn": 7200,
    "refreshExpiresIn": 604800
  },
  "traceId": "abc123"
}
```

说明：`accessToken` 和 `refreshToken` 明文只返回给客户端，服务端只保存 token hash。客户端不应解析 token 内容，应按 opaque token 处理。登录响应会返回 `roles` 和 `permissions`，前端按 `permissions` 控制入口展示，后端按权限字符串强制授权。

### POST /api/v1/auth/refresh

说明：使用 refresh token 换取新的 access token 和新的 refresh token。刷新成功后旧 access token 和旧 refresh token 都会失效。

请求：

```json
{
  "refreshToken": "refresh-token"
}
```

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "accessToken": "new-token",
    "refreshToken": "new-refresh-token",
    "expiresIn": 7200,
    "refreshExpiresIn": 604800
  },
  "traceId": "abc123"
}
```

### POST /api/v1/auth/logout

说明：吊销当前 Bearer token。接口需要携带当前登录 token。

请求头：

```http
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": null,
  "traceId": "abc123"
}
```

## 知识库接口

### POST /api/v1/knowledge-bases

说明：创建知识库

### GET /api/v1/knowledge-bases

说明：查询知识库列表。未传参数时返回全部知识库；传 `keyword` 时按知识库名称或说明做大小写不敏感模糊过滤。

请求参数：

- `keyword`：可选，按知识库名称或说明过滤

## 用户管理接口

### GET /api/v1/users

说明：管理员查询用户状态、角色、失败登录次数和临时锁定时间。仅 ADMIN 可访问。

请求参数：

- `page`：可选，从 0 开始，必须大于等于 0
- `size`：可选，默认 20，取值范围 `1..100`

不带分页参数时响应保持旧数组结构；带 `page` 或 `size` 时返回分页结构，字段同文档分页响应的 `items/page/size/totalItems/totalPages/hasPrevious/hasNext`。

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "id": 1,
      "username": "admin",
      "displayName": "系统管理员",
      "status": "ACTIVE",
      "failedLoginCount": 0,
      "lockedUntil": null,
      "roles": ["ADMIN"]
    }
  ],
  "traceId": "abc123"
}
```

### POST /api/v1/users/{userId}/unlock

说明：管理员解除用户因连续登录失败产生的临时锁定。该接口只清理 `failedLoginCount` 和 `lockedUntil`，不改变账号 `status`。仅 ADMIN 可访问。

### GET /api/v1/users/token-sessions

说明：管理员查询 token 会话。响应只返回 token hash 短指纹，不返回明文 token 或完整 hash。仅 ADMIN 可访问。

请求参数：

- `page`：可选，从 0 开始，必须大于等于 0
- `size`：可选，默认 20，取值范围 `1..100`

不带分页参数时响应保持旧数组结构并返回最近 200 条；带 `page` 或 `size` 时返回分页结构，字段同文档分页响应的 `items/page/size/totalItems/totalPages/hasPrevious/hasNext`。

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "id": 101,
      "userId": 1,
      "username": "admin",
      "tokenFingerprint": "9f64a747e1b9",
      "roleCodes": "ADMIN",
      "issuedAt": "2026-06-05T22:00:00",
      "expiresAt": "2026-06-06T00:00:00",
      "refreshExpiresAt": "2026-06-12T22:00:00",
      "lastRefreshedAt": null,
      "revokedAt": null,
      "accessTokenActive": true,
      "active": true
    }
  ],
  "traceId": "abc123"
}
```

### POST /api/v1/users/token-sessions/{sessionId}/revoke

说明：管理员吊销指定 token 会话。仅 ADMIN 可访问。

### POST /api/v1/users/{userId}/token-sessions/revoke

说明：管理员批量吊销指定用户的所有未吊销 token 会话。仅 ADMIN 可访问。

## 文档接口

### POST /api/v1/documents/upload

说明：上传文档并进入后台解析/索引队列；接口立即返回文档处理状态，不等待索引完成。

表单字段：

- knowledgeBaseId
- file

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 11,
    "fileName": "vpn-guide.md",
    "parseStatus": "PENDING",
    "indexStatus": "PENDING",
    "chunkCount": 0,
    "failureReason": null
  },
  "traceId": "abc123"
}
```

### GET /api/v1/documents/{id}

说明：查询文档详情和后台处理状态。

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 11,
    "fileName": "vpn-guide.md",
    "parseStatus": "SUCCESS",
    "indexStatus": "SUCCESS",
    "chunkCount": 3,
    "failureReason": null
  },
  "traceId": "abc123"
}
```

### GET /api/v1/documents

说明：查询指定知识库下的文档列表，按上传时间倒序返回。带 `page` 或 `size` 参数时返回分页结构；不带分页参数时保留旧数组结构，便于兼容已有调用。

请求参数：

- `knowledgeBaseId`
- `keyword`：可选，按文件名模糊过滤
- `indexStatus`：可选，支持 `PENDING` / `SUCCESS` / `FAILED`
- `page`：可选，从 0 开始，必须大于等于 0
- `size`：可选，默认 20，取值范围 `1..100`

不带分页参数响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "id": 11,
      "fileName": "vpn-guide.md",
      "parseStatus": "SUCCESS",
      "indexStatus": "SUCCESS",
      "chunkCount": 3,
      "failureReason": null
    }
  ],
  "traceId": "abc123"
}
```

带分页参数响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "items": [
      {
        "id": 11,
        "fileName": "vpn-guide.md",
        "parseStatus": "SUCCESS",
        "indexStatus": "SUCCESS",
        "chunkCount": 3,
        "failureReason": null
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1,
    "hasPrevious": false,
    "hasNext": false
  },
  "traceId": "abc123"
}
```

### POST /api/v1/documents/{id}/reindex

说明：重新提交文档后台索引任务。接口会将文档状态置为 `PENDING`，旧切片保留到新索引成功后再替换，避免重建失败导致知识库失去已有可用内容。

响应结构同 `GET /api/v1/documents/{id}`。

### POST /api/v1/documents/retry-failed

说明：批量重试指定知识库下的失败文档。只会处理 `indexStatus=FAILED` 的文档，返回已重新提交队列的文档列表。

请求参数：

- `knowledgeBaseId`

响应结构为文档数组，字段同 `GET /api/v1/documents` 不带分页参数时的列表项。

### DELETE /api/v1/documents/{id}

说明：删除文档记录、切片和本地存储文件。`PENDING` 状态文档暂不允许删除，避免后台索引任务和删除操作并发冲突。

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": null,
  "traceId": "abc123"
}
```

## 问答接口

### POST /api/v1/chat/ask

说明：基于知识库检索结果回答。若证据不足，会返回拒答型成功响应：`fallback=true`、`confidence=NONE`、`citations=[]`，`answer` 说明未找到足够依据。拒答门槛由 `app.rag.min-citation-count` 和 `app.rag.min-top-score` 控制。

请求：

```json
{
  "knowledgeBaseId": 1,
  "question": "VPN 无法连接应该怎么处理？"
}
```

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "conversationId": 1001,
    "answer": "请先检查账号状态，并确认客户端版本。",
    "citations": [
      {
        "documentId": 11,
        "chunkId": 201,
        "snippet": "VPN 连接失败时，先确认账号未被锁定。"
      }
    ],
    "confidence": "MEDIUM",
    "fallback": false
  },
  "traceId": "abc123"
}
```

### GET /api/v1/chat/history

说明：查询当前登录用户最近问答历史，默认 `limit=10`，取值范围 `1..50`。只返回当前用户自己的会话，不返回其它用户历史。

Query：

- `limit`：可选，最近会话数量

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "conversationId": 1001,
      "knowledgeBaseId": 1,
      "question": "VPN 无法连接应该怎么处理？",
      "answer": "先确认账号状态，再检查客户端版本和网络连接。",
      "citations": [],
      "confidence": "NONE",
      "fallback": true,
      "createdAt": "2026-06-06T00:00:00",
      "updatedAt": "2026-06-06T00:00:01"
    }
  ],
  "traceId": "abc123"
}
```

## 工单接口

### POST /api/v1/tickets/draft

说明：基于会话生成工单草稿

请求：

```json
{
  "conversationId": 1001
}
```

### POST /api/v1/tickets

说明：提交工单或提交待审批工单

### GET /api/v1/tickets/similar

说明：检索相似工单。后端按当前会话最近用户问题提取关键词，先基于工单标题做有限候选集查询，再对标题和描述做最终评分，避免每次查询全表扫描。

请求参数：

- `conversationId`

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "ticketId": 42,
      "title": "VPN 历史故障",
      "priority": "HIGH",
      "status": "OPEN",
      "score": 2,
      "matchedKeywords": ["vpn", "客户端"],
      "matchSummary": "命中关键词：vpn、客户端；来源：标题、描述"
    }
  ],
  "traceId": "abc123"
}
```

`score` 为命中关键词数量，`matchedKeywords` 保留问题关键词在历史工单标题/描述中的命中项，`matchSummary` 可直接用于前端解释推荐原因。

## 审批接口

### GET /api/v1/approvals/comment-templates

说明：查询审批备注模板。仅 ADMIN / APPROVER 可访问。模板按 `action` 区分 `approve` 和 `reject`，审批决策时只能使用与当前操作匹配的模板。

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "code": "APPROVE_EVIDENCE_SUFFICIENT",
      "action": "approve",
      "label": "证据充分，批准开单",
      "comment": "已核对知识库引用和工单内容，证据充分，同意创建正式工单。"
    }
  ],
  "traceId": "abc123"
}
```

### GET /api/v1/approvals/pending

说明：查询待审批任务

### POST /api/v1/approvals/{id}/approve

说明：审批通过

请求：

```json
{
  "templateCode": "APPROVE_EVIDENCE_SUFFICIENT",
  "comment": "已核对证据，同意创建正式工单。"
}
```

### POST /api/v1/approvals/{id}/reject

说明：审批驳回

请求：

```json
{
  "templateCode": "REJECT_EVIDENCE_INSUFFICIENT",
  "comment": "证据不足，请补充后重新提交。"
}
```

说明：`templateCode` 可为空；若填写模板且 `comment` 为空，系统使用模板默认备注。若模板与当前操作不匹配，返回 `VALIDATION_ERROR`。

## 审计接口

### GET /api/v1/audits

说明：按 traceId、eventType、targetType、targetId 查询审计日志。仅 ADMIN 可访问。

请求参数：

- `traceId`：可选，精确匹配 traceId
- `eventType`：可选，精确匹配事件类型
- `targetType`：可选，精确匹配目标类型
- `targetId`：可选，精确匹配目标 ID
- `page`：可选，从 0 开始，必须大于等于 0
- `size`：可选，默认 20，取值范围 `1..100`

不带分页参数时响应保持旧数组结构并返回最近 200 条内的过滤结果；带 `page` 或 `size` 时使用数据库过滤并返回分页结构，字段同文档分页响应的 `items/page/size/totalItems/totalPages/hasPrevious/hasNext`。

前端审计回看页使用分页模式，并会把 `traceId/eventType/targetType/targetId` 作为时间线过滤条件透传给该接口。

## 运营看板接口

### GET /api/v1/dashboard/operations

说明：查询只读运营汇总指标。仅具备 `dashboard:read` 权限的管理员可访问，用于首页总览任务队列、失败索引、待审批和高风险工单。

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "knowledgeBaseCount": 3,
    "documentCount": 12,
    "pendingIndexTaskCount": 4,
    "runningIndexTaskCount": 1,
    "failedIndexTaskCount": 2,
    "failedDocumentCount": 2,
    "pendingApprovalCount": 5,
    "activeHighRiskTicketCount": 6,
    "pendingHighRiskTicketCount": 3,
    "activeTokenSessionCount": 7,
    "totalIndexTaskCount": 10,
    "indexFailureRate": 0.2,
    "indexBacklogPressure": 0.42,
    "operationsBacklogCount": 15,
    "healthLevel": "CRITICAL",
    "alertCount": 3,
    "healthSummary": "存在需要立即处理的索引失败或高风险阻塞项。",
    "recommendedActions": [
      "处理失败索引任务和失败文档，优先查看失败原因后重试或修正文档格式。",
      "存在待审批高风险工单，请优先核对证据并完成审批。"
    ],
    "generatedAt": "2026-06-06T01:20:00"
  },
  "traceId": "abc123"
}
```

`healthLevel` 可取 `HEALTHY` / `ATTENTION` / `CRITICAL`。`indexFailureRate` 表示失败索引任务数 / 全部索引任务数，`indexBacklogPressure` 表示待处理索引任务数 / 文档数，均按两位小数返回；`operationsBacklogCount` 汇总待处理/运行/失败索引任务、待审批任务和待审批高风险工单。`recommendedActions` 由后端根据失败索引、失败率、积压压力、待审批、高风险工单和队列堆积情况生成，用于前端直接展示运营待办。

## AI 运行配置接口

### GET /api/v1/ai/runtime

说明：查询只读 AI 运行配置状态。仅具备 `dashboard:read` 权限的管理员可访问，用于前端 AI 配置页检查 chat/embedding 是否启用、模型 Bean 是否可用、凭证是否已配置以及 OpenAI-compatible baseUrl/path/model。接口不返回 API key 明文。

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "activeProfiles": ["mysql", "ai-openai"],
    "chat": {
      "enabled": true,
      "modelAvailable": true,
      "credentialConfigured": true,
      "provider": "openai",
      "baseUrl": "https://relay.example.com/v1",
      "path": "/chat/completions",
      "model": "gpt-5.5"
    },
    "embedding": {
      "enabled": false,
      "modelAvailable": false,
      "credentialConfigured": true,
      "provider": "none",
      "baseUrl": "https://relay.example.com/v1",
      "path": "/embeddings",
      "model": "text-embedding-3-small"
    },
    "readinessLevel": "READY",
    "warnings": [
      "AI runtime configuration is ready for enabled components."
    ],
    "generatedAt": "2026-06-06T02:10:00"
  },
  "traceId": "abc123"
}
```

`readinessLevel` 可取 `READY` / `PARTIAL` / `DISABLED`。`credentialConfigured` 只表示 Spring 环境中存在非空凭证配置，不暴露凭证内容。

## 错误码初稿

- `OK`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `VALIDATION_ERROR`
- `DOCUMENT_PARSE_FAILED`
- `INDEX_BUILD_FAILED`
- `RAG_NO_EVIDENCE`
- `TOOL_CALL_FAILED`
- `APPROVAL_REQUIRED`
- `APPROVAL_NOT_FOUND`
