# Enterprise Knowledge & Ticket Agent

一个面向企业内部场景的知识库与工单协同 Agent 项目。

目标不是做一个“会聊天”的演示，而是做一个可以落到企业场景里的最小可用系统：

- 上传制度、产品、排障文档
- 基于检索返回带引用的回答
- 结合历史工单生成新工单草稿
- 高风险动作进入人工审批
- 全流程可审计、可追溯

## 当前状态

- 仓库当前阶段：可运行单体 MVP，持续补齐产品化能力
- 开发目标：完善企业知识库、RAG 问答、工单协同和审计闭环
- 优先级：闭环优先，架构炫技靠后

## 文档导航

- [项目概览](docs/00-project-overview.md)
- [MVP 范围](docs/01-mvp-scope.md)
- [业务流程](docs/02-business-flow.md)
- [需求清单](docs/03-requirements.md)
- [领域模型](docs/04-domain-model.md)
- [数据库设计](docs/05-database-design.md)
- [API 规范](docs/06-api-spec.md)
- [RAG 设计](docs/07-rag-design.md)
- [Tool Calling 设计](docs/08-tool-calling-design.md)
- [认证与 RBAC](docs/09-auth-rbac-design.md)
- [审批流设计](docs/10-approval-flow.md)
- [审计与可观测性](docs/11-audit-observability.md)
- [错误处理](docs/12-error-handling.md)
- [测试计划](docs/13-test-plan.md)
- [开发计划](docs/14-dev-plan.md)
- [部署运行手册](docs/15-deployment-runbook.md)
- [演示脚本](docs/16-demo-script.md)
- [Backlog](docs/17-backlog.md)
- [已知问题](docs/18-known-issues.md)
- [前端待开发](docs/19-frontend-backlog.md)

## 计划技术栈

- Java 17（当前本机可直接运行，后续可升级到 21）
- Spring Boot 3
- Spring AI 1.1.7
- PostgreSQL + pgvector
- Redis
- MinIO
- Spring Security
- MyBatis-Plus 或 JPA
- Docker Compose

## 当前实现

当前仓库已经初始化为一个可运行的单体 Spring Boot MVP，包含：

- 登录接口和基于 Bearer token 的最小鉴权
- BCrypt 种子密码迁移、数据库持久化 token 会话、access/refresh token 轮换、可配置 token 过期时间、基础 RBAC 越权保护
- 登录成功、失败、临时锁定和登出审计；登录失败临时锁定、管理员用户状态查看和账号解锁
- 管理员 token 会话查看、单会话吊销和按用户批量吊销
- 登录响应返回细粒度权限清单，后端按权限字符串授权，前端按权限收敛入口
- 前端角色感知导航，普通用户不展示管理员/审批入口，也不会自动请求管理员文档管理接口
- 管理员运营指标总览，展示索引队列、失败索引、待审批、高风险工单和活跃 token 会话
- 运营总览返回健康等级、告警数量、健康摘要和建议动作，帮助管理员优先处理失败索引和高风险阻塞项
- 管理员用户状态、token 会话和审计日志分页查询，避免管理面板全量加载
- 知识库创建
- 文档上传、本地存储、TXT/Markdown/PDF/DOCX 文本提取
- 文档列表服务端分页、搜索、状态筛选、后台索引状态轮询、失败重试、删除和重建索引入口
- 持久化文档索引任务队列、文本切片和入库
- 基于关键词匹配的默认问答链路
- 问答区支持最近会话历史，当前用户可恢复历史回答继续生成工单
- 相似工单检索使用关键词候选集和内存评分，避免全表加载历史工单
- 可选 Spring AI + OpenAI chat/embedding 接入
- 工单草稿生成、提交、审批，审批备注支持标准模板和人工编辑
- 审计日志查询

默认模式不需要 API key，会使用本地关键词检索和规则式答案生成。启用 `ai-openai` profile 后，会走 OpenAI-compatible Chat API；默认 chat 模型为 `gpt-5.4`，适配官方 OpenAI 或中转站。embedding 默认关闭，需要时单独启用。

## 快速启动

### 运行测试

```bash
mvn test
```

### 启动应用

```bash
mvn spring-boot:run
```

应用默认使用本地 H2 文件数据库和 `storage/` 目录保存上传文档。

如需按样例准备本地环境变量，可参考 [.env.example](.env.example)。不要把真实 API key 提交到仓库。

access token 默认 2 小时过期，可通过 `app.security.token-ttl-seconds` 或 `APP_SECURITY_TOKEN_TTL_SECONDS` 覆盖；refresh token 默认 7 天过期，可通过 `app.security.refresh-token-ttl-seconds` 或 `APP_SECURITY_REFRESH_TOKEN_TTL_SECONDS` 覆盖。token 会话写入 `auth_token_session`，只保存 access/refresh token 的 SHA-256 hash，不落明文；刷新成功后会轮换 access token 和 refresh token。用户登出、refresh token 过期或定时清理任务都会把会话标记为 `revoked_at`，清理间隔可通过 `app.security.expired-token-cleanup-delay-ms` 或 `APP_SECURITY_EXPIRED_TOKEN_CLEANUP_DELAY_MS` 覆盖。登录失败会累计到 `app_user.failed_login_count`，默认 5 次失败后临时锁定 15 分钟，可通过 `APP_SECURITY_MAX_FAILED_LOGIN_ATTEMPTS` 和 `APP_SECURITY_LOGIN_LOCKOUT_MINUTES` 调整。登录成功、登录失败、账号锁定、token 刷新和登出会写入审计日志，payload 不包含密码、明文 token 或 token hash。

问答拒答门槛可通过 `app.rag.min-citation-count` 和 `app.rag.min-top-score` 配置。证据不足时接口返回 `fallback=true`、`confidence=NONE`、空引用列表和补充问题提示，避免在弱证据下生成过度确定的答案。

文档上传会先返回 `PENDING` 状态，并写入 `document_index_task` 持久化任务表。后台 worker 会批量领取任务，完成解析、切片和索引；应用重启或任务执行超时后，未完成任务会被重新领取。管理员可分页查询文档列表、按文件名/状态在服务端筛选、查看失败原因、批量重试失败文档、删除非处理中状态文档，并对已有文档重新提交索引任务；重建失败时旧切片会保留到下一次成功索引。索引 worker 可通过以下配置调整：

- `app.document-index.core-pool-size`
- `app.document-index.max-pool-size`
- `app.document-index.queue-capacity`
- `app.document-index.max-attempts`
- `app.document-index.batch-size`
- `app.document-index.poll-delay-ms`
- `app.document-index.stale-timeout-seconds`

### 启动前端简版

前端位于 `frontend/`，本地开发时通过 Vite 代理访问 Spring Boot 的 `/api`：

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。后端需先在 `http://localhost:8080` 启动。

前端环境变量样例见 [frontend/.env.example](frontend/.env.example)。

前端验证：

```bash
cd frontend
npm run build
npm run e2e
```

`npm run e2e` 会通过 Playwright 覆盖“登录 -> 创建知识库 -> 上传文档 -> 文档状态刷新 -> 提问 -> 生成并提交工单”的核心链路。运行前需要后端可访问 `http://localhost:8080`。

如果后端运行在其他端口，可通过环境变量覆盖 Vite 代理：

```powershell
$env:VITE_API_TARGET='http://localhost:18080'
$env:FRONTEND_PORT='5174'
$env:PLAYWRIGHT_REUSE_SERVER='false'
npm run e2e
```

### 启动 OpenAI-compatible 模式

PowerShell:

```powershell
$env:OPENAI_API_KEY='your-api-key-or-relay-key'
$env:OPENAI_BASE_URL='https://your-relay.example.com/v1'
$env:OPENAI_CHAT_MODEL='gpt-5.5'
mvn spring-boot:run "-Dspring-boot.run.profiles=ai-openai"
```

如果要按本机 Codex 的 `C:\Users\888\.codex\config.toml` 对齐，目前对应关系是：

- `base_url` -> `OPENAI_BASE_URL`
- `model` -> `OPENAI_CHAT_MODEL`
- `requires_openai_auth = true` -> 仍需要设置 `OPENAI_API_KEY` 或 `OPENAI_COMPATIBLE_API_KEY`

可选环境变量：

- `OPENAI_BASE_URL`，默认 `https://api.openai.com/v1`，中转站通常直接填它提供的 `/v1` 地址
- `OPENAI_COMPATIBLE_API_KEY`，当不想使用 `OPENAI_API_KEY` 变量名时可用
- `OPENAI_CHAT_MODEL`，默认 `gpt-5.4`
- `OPENAI_CHAT_COMPLETIONS_PATH`，默认 `/chat/completions`
- `OPENAI_EMBEDDING_PROVIDER`，默认 `none`；需要 embedding 时设为 `openai`
- `OPENAI_EMBEDDING_ENABLED`，默认 `false`；需要 embedding 时设为 `true`
- `OPENAI_EMBEDDING_MODEL`，默认 `text-embedding-3-small`
- `OPENAI_EMBEDDINGS_PATH`，默认 `/embeddings`

如果中转站只提供 chat 模型，不要开启 embedding。此时系统仍会使用关键词检索，答案由配置的 chat 模型生成。

注意：Codex 的 `wire_api = "responses"` 是 Codex 客户端自己的协议配置；本项目当前通过 Spring AI 调用 OpenAI-compatible `/chat/completions`。如果中转站只支持 Responses API 而不支持 Chat Completions，需要更换兼容端点或后续新增 Responses API 适配。

### 启动 MySQL 模式

MySQL profile 使用业务库 `agentdb`，连接默认值为 `root / 123456 / localhost:3306`。需要你先明确创建业务库；之后启动应用时 Flyway 会在该库内创建/更新项目表。

PowerShell:

```powershell
$env:MYSQL_JDBC_URL='jdbc:mysql://localhost:3306/agentdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:MYSQL_USERNAME='root'
$env:MYSQL_PASSWORD='123456'
mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

如果同时启用 MySQL 和 OpenAI-compatible：

```powershell
$env:OPENAI_API_KEY='your-api-key-or-relay-key'
$env:OPENAI_BASE_URL='https://your-relay.example.com/v1'
$env:OPENAI_CHAT_MODEL='gpt-5.5'
$env:MYSQL_JDBC_URL='jdbc:mysql://localhost:3306/agentdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:MYSQL_USERNAME='root'
$env:MYSQL_PASSWORD='123456'
mvn spring-boot:run "-Dspring-boot.run.profiles=mysql,ai-openai"
```

当前约定：除非你明确授权，我只会对 MySQL 执行 `SELECT`、`SHOW`、`DESCRIBE`、`information_schema` 这类读操作；建库、迁移、写入、删除和重建库命令只提供给你确认后手动执行。

## 默认账号

- `admin / admin123`
- `user / user123`
- `support / support123`
- `approver / approver123`

## MVP 交付定义

MVP 完成不以代码量判断，而以这条链路是否跑通判断：

1. 管理员上传文档
2. 系统完成切片、索引、入库
3. 用户提问并收到带引用的回答
4. 用户触发生成工单草稿
5. 系统给出草稿并支持审批
6. 审计日志可以回看全过程

## 后续建议

先按文档把后端骨架搭起来，再补前端。第一阶段不要拆微服务，不要做多 Agent。
