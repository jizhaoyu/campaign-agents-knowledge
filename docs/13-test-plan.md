# 测试计划

## 测试目标

验证项目核心闭环稳定可用，而不是追求表面覆盖率。

## 测试层次

### 单元测试

- 文档切片逻辑
- 检索结果过滤逻辑
- 工单草稿组装逻辑
- 相似工单关键词分词、排序和相似原因生成逻辑
- 审批状态流转逻辑

### 集成测试

- 登录与鉴权
- BCrypt 种子账号登录
- Token TTL 过期拒绝访问
- token 会话只持久化 hash，应用内 token store 重建后仍可解析未过期 token
- refresh token 可在 access token 过期后换取新 access token，并轮换 refresh token
- 登出后当前 token 被吊销，再访问受保护接口返回 401
- 登录成功、登录失败、触发账号锁定、token 刷新和登出都会写入审计，且不记录密码、明文 token 或 token hash
- 连续登录失败达到阈值后账号临时锁定并返回 423
- 管理员可查询用户状态并解锁临时锁定账号
- 管理员可查看 token 会话列表，且响应不暴露明文 token 或完整 hash
- 管理员可吊销单个 token 会话，吊销后该 token 无法访问受保护接口
- 管理员可批量吊销指定用户 token 会话
- 登录响应返回权限清单，后端按权限字符串强制授权
- 管理员可读取运营汇总指标，普通用户访问运营看板接口返回 403
- 运营看板返回健康等级、告警数量、健康摘要和建议动作
- 管理员可读取 AI 运行配置状态，普通用户访问返回 403，响应不暴露 API key 明文
- 管理员用户列表、token 会话列表和审计日志支持分页参数，响应不暴露密码、明文 token 或完整 token hash
- 前端审计页支持按 traceId、eventType、targetType、targetId 过滤，分页请求保留过滤参数，并以时间线展示 payload 预览
- 前端登录后进入 `/dashboard`，导航切换到 `/knowledge`、`/chat`、`/tickets`、`/approvals`、`/ai-config`、`/users`、`/sessions`、`/audits` 时只渲染当前页面，刷新后保持路由
- 前端用户、token 会话和审计入口分别按 `user:admin`、`token-session:admin`、`audit:read` 权限展示
- 前端 AI 配置页展示 baseUrl、path、model、embedding 开关和凭证配置状态，但不展示 API key
- 普通用户访问用户管理接口返回 403
- 普通用户访问管理员接口返回 403
- 审批人具备审批权限但不能提交工单或管理知识库
- 文档上传到索引入库
- DOCX 文档上传后可提取正文并进入索引
- 文档列表查询与重建索引
- 文档列表支持服务端分页、文件名搜索、索引状态过滤和分页参数校验
- 文档列表兼容旧版数组响应时，仍支持关键词和索引状态过滤
- 文档索引任务落库、成功状态、失败状态和重建任务记录
- 问答接口返回引用
- 问答无证据或低于强拒答阈值时返回 `fallback=true`、`confidence=NONE` 且不返回引用
- 当前用户可查询自己的最近问答历史，历史接口不返回其它用户会话
- 工单草稿生成
- 审批备注模板可查询、可用于审批决策，且模板来源进入审计
- 审批通过后工单状态变化
- 相似工单检索可从有限候选集中命中相关历史工单，返回命中关键词和相似原因，避免全表扫描型实现
- MySQL 迁移脚本静态覆盖全部 JPA 实体表和持久化列，避免实体字段新增后漏改 `db/mysql` 迁移
- Dockerfile 和 Compose 部署契约有静态测试守护，防止构建阶段、MySQL 服务、应用环境变量和 volume 声明被误删

### 手工验收

- 端到端演示脚本
- 异常输入与拒答场景
- 权限边界检查

## 核心测试场景

1. 上传合法文档并成功索引
2. 上传 DOCX 文档并成功索引，提问时引用该 DOCX
3. 上传不支持格式并收到明确报错
4. 查询文档列表并对已有文档重建索引
5. 文档索引任务在 `document_index_task` 中记录最终状态
6. 提问命中文档并返回引用
7. 提问无证据时系统拒答
8. 提高 `app.rag.min-citation-count` 或 `app.rag.min-top-score` 后，弱证据命中也拒答
9. 当前用户查询最近问答历史成功，且不包含其它用户会话
10. 生成工单草稿成功
11. 高优先级工单进入审批
12. 审批备注模板按通过/驳回区分，错误动作模板被拒绝
13. 审批通过后工单进入 OPEN
14. 审批驳回后工单保持 REJECTED
15. 普通用户无法查看全部审计日志
16. 过期 token 无法访问受保护接口
17. 登录 token 明文不落库，`auth_token_session.token_hash` 和 `refresh_token_hash` 为固定长度 hash
18. access token 过期后受保护接口返回 401，但 refresh token 未过期时可换取新 token
19. 刷新成功后旧 access token 和旧 refresh token 都不能继续使用，并写入 `USER_TOKEN_REFRESHED` 审计
20. 登出后 `auth_token_session.revoked_at` 被写入，原 token 无法继续使用
21. 登录成功写入 `USER_LOGIN_SUCCEEDED` 审计，actorId 为登录用户 id，payload 不包含密码或 token
22. 连续错误密码达到阈值后 `app_user.locked_until` 被写入，正确密码也暂时不能登录，并写入 `USER_LOGIN_FAILED` 和 `USER_LOGIN_LOCKED` 审计
23. 登出成功写入 `USER_LOGOUT` 审计
24. 管理员解锁账号后 `failed_login_count=0` 且 `locked_until=null`，并写入 `USER_UNLOCKED` 审计
25. 管理员吊销 token 会话后 `auth_token_session.revoked_at` 被写入，原 token 返回 401
26. 登录响应包含 `permissions`，token 解析后包含权限 authority
27. 审批人可访问审批队列，但访问工单提交和知识库管理接口返回 403
28. 普通用户访问 token 会话管理接口返回 403
29. 运营看板返回知识库、文档、索引队列、失败索引、待审批、高风险工单和活跃 token 会话计数
30. 运营看板在存在失败索引或待审批高风险工单时返回 `CRITICAL` 和建议动作列表
31. 文档列表带 `page/size` 时返回分页结构，搜索和状态筛选在服务端生效，非法分页参数返回 `VALIDATION_ERROR`
32. 管理端用户、token 会话和审计列表带 `page/size` 时返回分页结构，非法分页参数返回 `VALIDATION_ERROR`
33. 相似工单检索根据问题关键词命中相关历史工单，接口返回 score 大于 0 的前 5 条，并包含 `matchedKeywords` 与 `matchSummary`
34. MySQL 迁移脚本覆盖全部 JPA 实体表和持久化列，`MysqlSchemaEntityAlignmentTest` 不连接 MySQL 也能发现漏改迁移
35. 前端普通用户只能看到允许的路由入口，直接访问无权限路由会回到可见的默认页
36. 前端路由切换后刷新页面仍保持当前业务页，浏览器前进后退同步当前导航高亮
37. 前端审计页按 traceId 和事件过滤时，请求包含过滤参数并返回对应时间线，traceId 可复制
38. AI 运行配置接口返回 chat/embedding 状态、readinessLevel 和 warnings，响应体不包含 API key 明文
39. 前端 AI 配置页只展示凭证是否已配置，管理员可刷新运行配置，普通用户看不到入口
40. 前端相似工单列表展示命中关键词和相似原因，帮助用户判断是否复用历史工单经验
41. 前端回答引用和提交后的工单号可复制，并通过通知反馈复制结果

## 完成标准

- 核心接口具备最小集成测试
- 核心业务链路具备手工验收记录
- GitHub Actions 对后端测试、前端构建、Playwright E2E、前后端一体化 Jar 打包和 Jar 静态资源检查提供自动门禁
- Dockerfile 能声明前端构建和前后端一体化 Jar 打包阶段，Compose 配置能声明 MySQL 业务库、应用环境变量、健康检查和持久化 volume
- 没有已知的高优先级阻塞问题
