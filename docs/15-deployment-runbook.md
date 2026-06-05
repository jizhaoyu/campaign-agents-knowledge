# 部署运行手册

## 目标环境

第一版优先支持本地开发和单机 Docker Compose。

## 依赖服务

- PostgreSQL
- Redis
- MinIO
- 向量能力使用 pgvector

当前代码也提供 MySQL profile，适合本机或单机部署先跑通业务闭环。MySQL 模式的迁移脚本位于 `src/main/resources/db/mysql`。

## 环境变量建议

- `SPRING_PROFILES_ACTIVE`
- `APP_SECURITY_TOKEN_TTL_SECONDS`
- `APP_SECURITY_REFRESH_TOKEN_TTL_SECONDS`
- `APP_SECURITY_EXPIRED_TOKEN_CLEANUP_DELAY_MS`
- `APP_SECURITY_EXPIRED_TOKEN_CLEANUP_INITIAL_DELAY_MS`
- `APP_SECURITY_MAX_FAILED_LOGIN_ATTEMPTS`
- `APP_SECURITY_LOGIN_LOCKOUT_MINUTES`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `MYSQL_JDBC_URL`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `REDIS_HOST`
- `MINIO_ENDPOINT`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `OPENAI_API_KEY`
- `OPENAI_COMPATIBLE_API_KEY`
- `OPENAI_BASE_URL`
- `OPENAI_CHAT_MODEL`
- `OPENAI_CHAT_COMPLETIONS_PATH`
- `OPENAI_EMBEDDING_PROVIDER`
- `OPENAI_EMBEDDING_ENABLED`
- `OPENAI_EMBEDDING_MODEL`
- `OPENAI_EMBEDDINGS_PATH`
- `APP_RAG_MIN_CITATION_COUNT`
- `APP_RAG_MIN_TOP_SCORE`

## 本地启动顺序

1. 启动 PostgreSQL
2. 启动 Redis
3. 启动 MinIO
4. 初始化数据库表
5. 启动 Spring Boot 应用

当前 MVP 可先不启动 Redis/MinIO/PostgreSQL，直接使用 H2、本地文件存储和关键词检索：

```powershell
mvn spring-boot:run
```

### 前后端一体化 Jar

生产或演示环境可以把前端构建产物打进 Spring Boot Jar，减少单独部署 Vite/Nginx 的步骤：

```powershell
cd frontend
npm run build
cd ..
mvn -Pfrontend package
java -jar target/knowledge-ticket-agent-0.0.1-SNAPSHOT.jar
```

`frontend` profile 只复制当前构建需要的 `index.html`、`favicon.svg`、`assets/index.js` 和 `assets/index.css`，并会先清理 `target/classes/static`，避免旧 hash 静态资源进入 Jar。应用会对 `/dashboard`、`/knowledge`、`/chat`、`/tickets`、`/approvals`、`/ai-config`、`/users`、`/sessions`、`/audits` 做 SPA fallback；`/api/**` 仍按 Bearer token 鉴权。

### MySQL 启动

业务库名称建议使用 `agentdb`。在未明确获得授权前，自动化助手只能做 MySQL 读操作核对，不执行建库、迁移、删除、更新等写操作。

应用启动命令：

```powershell
$env:MYSQL_JDBC_URL='jdbc:mysql://localhost:3306/agentdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:MYSQL_USERNAME='root'
$env:MYSQL_PASSWORD='123456'
mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

启动 `mysql` profile 后，Flyway 会使用 `classpath:db/mysql` 迁移脚本在目标业务库中建表和写入种子账号。

代码侧通过 `MysqlSchemaEntityAlignmentTest` 静态比对 `src/main/resources/db/mysql/*.sql` 与 JPA 实体表/列，防止实体字段新增后漏写 MySQL 迁移。该测试只读本地 SQL 文件和注解，不连接 MySQL；真实环境仍需要在你明确授权后再启动 `mysql` profile 做运行验收。

### OpenAI-compatible 启动

本项目通过 Spring AI 调用 OpenAI-compatible Chat Completions API。

```powershell
$env:OPENAI_API_KEY='your-api-key-or-relay-key'
$env:OPENAI_BASE_URL='https://your-openai-compatible-base-url.example/v1'
$env:OPENAI_CHAT_MODEL='gpt-5.5'
mvn spring-boot:run "-Dspring-boot.run.profiles=ai-openai"
```

Codex 本机配置可作为参考映射：

- `C:\Users\888\.codex\config.toml` 的 `base_url` 对应 `OPENAI_BASE_URL`
- `model` 对应 `OPENAI_CHAT_MODEL`
- 项目仍需要通过 `OPENAI_API_KEY` 或 `OPENAI_COMPATIBLE_API_KEY` 提供 key

注意：Codex 的 `wire_api = "responses"` 不等同于本项目的 Spring AI Chat Completions 调用。如果供应商不支持 `/chat/completions`，当前 `ai-openai` profile 不能直接使用该供应商。

启动后可用管理员账号打开前端 `AI配置` 页面，或调用 `GET /api/v1/ai/runtime` 检查 active profiles、chat/embedding provider、baseUrl、path、model、凭证是否配置和模型 Bean 是否存在。该接口只返回布尔状态，不返回 API key 明文。

## 启动后检查

- 登录接口可用
- 登录响应 `expiresIn` 和 `refreshExpiresIn` 符合预期
- `auth_token_session` 会写入 access/refresh token hash，且不包含明文 token
- `/api/v1/auth/refresh` 可用，刷新后旧 access token 和旧 refresh token 不可继续使用
- 成功登录写入 `USER_LOGIN_SUCCEEDED` 审计，失败登录写入 `USER_LOGIN_FAILED`，账号锁定写入 `USER_LOGIN_LOCKED`
- token 刷新写入 `USER_TOKEN_REFRESHED` 审计
- 连续错误密码达到阈值后返回 423，`app_user.locked_until` 有值
- 登出成功后当前 token 被吊销，并写入 `USER_LOGOUT` 审计
- 管理员用户列表可查看锁定账号，解锁后会写入 `USER_UNLOCKED` 审计
- 管理员 token 会话列表可查看活跃会话；单会话吊销写入 `TOKEN_SESSION_REVOKED`，按用户批量吊销写入 `USER_TOKEN_SESSIONS_REVOKED`
- 文档上传接口可用
- 问答接口可返回结果
- 无证据或低于 RAG 阈值的问题返回 `fallback=true`、`confidence=NONE`
- 审批列表接口可访问
- 管理员可访问 `/api/v1/ai/runtime`，响应不包含 `OPENAI_API_KEY`、`OPENAI_COMPATIBLE_API_KEY` 或实际 key 值

## 文档索引线程池

文档上传后会先写入 `document_index_task` 持久化任务表，再由后台 worker 领取执行。小规模本地环境可使用默认配置；并发上传或大文件较多时，需要根据机器资源调整：

- `app.document-index.core-pool-size`
- `app.document-index.max-pool-size`
- `app.document-index.queue-capacity`
- `app.document-index.max-attempts`
- `app.document-index.batch-size`
- `app.document-index.poll-delay-ms`
- `app.document-index.stale-timeout-seconds`

如果任务持续堆积，应优先观察 `document_index_task.status`、`next_run_at`、`last_error` 和 worker 日志，再调整 batch size、线程池或拆出独立 worker 实例。

## 常见故障

### 登录后很快失效

检查：

- `app.security.token-ttl-seconds`
- `app.security.refresh-token-ttl-seconds`
- `APP_SECURITY_TOKEN_TTL_SECONDS`
- `APP_SECURITY_REFRESH_TOKEN_TTL_SECONDS`
- `auth_token_session.expires_at`
- `auth_token_session.refresh_expires_at`
- `auth_token_session.revoked_at`
- 应用服务器和数据库服务器时间是否一致
- Flyway 是否已迁移 `auth_token_session`

当前 token 会话保存在数据库中，应用重启本身不会让 refresh token 未过期的会话失效。如果重启后全部登录态失效，应优先检查是否切换了数据库、迁移是否完整、access/refresh TTL 是否被环境变量覆盖为过短值。

### 登录提示账号临时锁定

检查：

- `app_user.failed_login_count`
- `app_user.locked_until`
- `APP_SECURITY_MAX_FAILED_LOGIN_ATTEMPTS`
- `APP_SECURITY_LOGIN_LOCKOUT_MINUTES`

如果是测试环境误触发，可等待锁定时间过期；生产环境可由 ADMIN 在用户管理接口中解锁。解锁只清理临时锁定，不会改变被人工禁用的账号状态。

### 文档上传成功但无法检索

检查：

- 文档解析状态
- 索引状态
- `document_index_task` 最近任务状态与 `last_error`
- 向量字段是否写入

### 问答超时

检查：

- 模型服务连接
- 检索耗时
- 重排耗时

### OpenAI-compatible 启动失败

检查：

- `OPENAI_API_KEY` 或 `OPENAI_COMPATIBLE_API_KEY` 是否存在
- `OPENAI_BASE_URL` 是否包含 `/v1`
- 供应商是否支持 `/chat/completions`
- `OPENAI_CHAT_MODEL` 是否为供应商实际开放的模型名
- 如果供应商不支持 embeddings，确认 `OPENAI_EMBEDDING_PROVIDER=none` 且 `OPENAI_EMBEDDING_ENABLED=false`
- 访问前端 `AI配置` 页或 `/api/v1/ai/runtime`，查看 `readinessLevel`、`warnings`、`modelAvailable` 和 `credentialConfigured`

### 审批通过后未开单

检查：

- 审批状态
- 工单状态机
- createTicket 工具调用日志
