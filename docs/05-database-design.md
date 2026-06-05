# 数据库设计

## 设计说明

- 数据库建议：PostgreSQL
- 向量存储：pgvector
- 审计日志与业务表同库，后续可拆
- 第一版优先保证可开发，不追求极致范式

## 表：user

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| username | varchar(64) | 登录名，唯一 |
| password_hash | varchar(255) | 密码哈希 |
| display_name | varchar(64) | 展示名 |
| status | varchar(16) | ACTIVE/LOCKED |
| failed_login_count | int | 连续登录失败次数 |
| locked_until | timestamp | 临时锁定截止时间 |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

## 表：role

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| code | varchar(64) | 角色编码，唯一 |
| name | varchar(64) | 角色名称 |
| created_at | timestamp | 创建时间 |

## 表：user_role

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| user_id | bigint | 用户 ID |
| role_id | bigint | 角色 ID |

## 表：auth_token_session

用于服务端 Bearer token 会话。明文 access/refresh token 只在登录或刷新响应中返回，数据库只保存 SHA-256 hash。刷新时会轮换 access token 和 refresh token。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| token_hash | varchar(64) | access token SHA-256 hash，唯一 |
| user_id | bigint | 用户 ID |
| username | varchar(64) | 登录名快照 |
| role_codes | varchar(255) | 角色编码快照，逗号分隔 |
| issued_at | timestamp | 签发时间 |
| expires_at | timestamp | access token 过期时间 |
| refresh_token_hash | varchar(64) | refresh token SHA-256 hash，唯一 |
| refresh_expires_at | timestamp | refresh token 过期时间 |
| last_refreshed_at | timestamp | 最近一次刷新时间 |
| revoked_at | timestamp | 吊销时间，未吊销为空 |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

建议索引：

- uk_auth_token_session_token_hash
- uk_auth_token_session_refresh_token_hash
- idx_auth_token_session_user_id
- idx_auth_token_session_expires_at
- idx_auth_token_session_refresh_expires_at

## 表：knowledge_base

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| name | varchar(128) | 知识库名称 |
| description | varchar(255) | 描述 |
| status | varchar(16) | ACTIVE/INACTIVE |
| created_by | bigint | 创建人 |
| created_at | timestamp | 创建时间 |

## 表：document

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| knowledge_base_id | bigint | 所属知识库 |
| file_name | varchar(255) | 原文件名 |
| file_type | varchar(32) | PDF/MD/TXT |
| object_key | varchar(255) | 对象存储路径 |
| parse_status | varchar(16) | PENDING/SUCCESS/FAILED |
| index_status | varchar(16) | PENDING/SUCCESS/FAILED |
| uploaded_by | bigint | 上传人 |
| failure_reason | varchar(512) | 解析或索引失败原因 |
| created_at | timestamp | 创建时间 |

建议索引：

- idx_document_kb_id
- idx_document_parse_status

## 表：document_index_task

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| document_id | bigint | 文档 ID |
| status | varchar(16) | PENDING/RUNNING/SUCCESS/FAILED |
| attempt_count | int | 已尝试次数 |
| max_attempts | int | 最大尝试次数 |
| trace_id | varchar(64) | 首次入队请求链路 ID |
| last_error | varchar(512) | 最近一次失败原因 |
| next_run_at | timestamp | 下次可领取时间 |
| started_at | timestamp | 最近一次开始执行时间 |
| finished_at | timestamp | 任务完成时间 |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

建议索引：

- idx_document_index_task_due
- idx_document_index_task_document_id

当前实现为单体内置持久化任务队列，不依赖 Redis 或外部 MQ。worker 会按 `next_run_at` 领取 PENDING 任务，并把超时 RUNNING 任务重新置为 PENDING。

## 表：document_chunk

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| document_id | bigint | 所属文档 |
| chunk_no | int | 切片序号 |
| content | text | 切片文本 |
| token_count | int | token 数 |
| start_offset | int | 起始偏移 |
| end_offset | int | 结束偏移 |
| embedding | vector | 向量字段 |
| created_at | timestamp | 创建时间 |

建议索引：

- idx_chunk_document_id
- ivfflat 或 hnsw 向量索引

## 表：conversation

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| user_id | bigint | 发起人 |
| knowledge_base_id | bigint | 问答使用的知识库 |
| status | varchar(16) | OPEN/CLOSED |
| created_at | timestamp | 创建时间 |

## 表：message

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| conversation_id | bigint | 会话 ID |
| role | varchar(16) | USER/ASSISTANT/SYSTEM |
| content | text | 消息内容 |
| citation_json | jsonb | 引用片段 |
| created_at | timestamp | 创建时间 |

## 表：ticket

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| conversation_id | bigint | 来源会话 |
| title | varchar(255) | 标题 |
| description | text | 描述 |
| priority | varchar(16) | LOW/MEDIUM/HIGH |
| status | varchar(16) | DRAFT/PENDING_APPROVAL/OPEN/REJECTED/CLOSED |
| assignee_id | bigint | 负责人 |
| created_by | bigint | 创建人 |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

## 表：approval_task

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| target_type | varchar(32) | TICKET_CREATE 等 |
| target_id | bigint | 目标 ID |
| approver_id | bigint | 审批人 |
| status | varchar(16) | PENDING/APPROVED/REJECTED |
| comment | varchar(255) | 审批备注 |
| created_at | timestamp | 创建时间 |
| decided_at | timestamp | 决策时间 |

## 表：audit_log

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| actor_id | bigint | 操作人 |
| event_type | varchar(64) | 事件类型 |
| target_type | varchar(32) | 目标类型 |
| target_id | bigint | 目标 ID |
| trace_id | varchar(64) | 调用链 ID |
| payload_json | jsonb | 扩展载荷 |
| created_at | timestamp | 创建时间 |

建议索引：

- idx_audit_trace_id
- idx_audit_target
- idx_audit_event_type

## 状态枚举建议

- 文档解析状态：PENDING, SUCCESS, FAILED
- 文档索引状态：PENDING, SUCCESS, FAILED
- 文档索引任务状态：PENDING, RUNNING, SUCCESS, FAILED
- 会话状态：OPEN, CLOSED
- 工单状态：DRAFT, PENDING_APPROVAL, OPEN, REJECTED, CLOSED
- 审批状态：PENDING, APPROVED, REJECTED
