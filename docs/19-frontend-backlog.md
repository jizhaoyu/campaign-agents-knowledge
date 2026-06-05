# 前端待开发与优化点

## 当前简版范围

- 技术栈：React + Vite + TypeScript + 原生 CSS 变量
- 页面形态：客户端路由工作台，已拆分为 `components/`、`hooks/`、`pages/`
- 覆盖链路：登录、知识库创建/选择、文档上传、知识问答、工单草稿、提交工单、审批、审计回看
- 设计方向：企业 AI 控制台，浅色蓝灰系，Swiss 信息排版，优先保证业务闭环清晰
- 验证方式：`npm run build` + `npm run e2e`

## P1 - 下一轮优先

暂无

## 已完成

- 2026-06-06：增加知识库搜索，后端 `GET /knowledge-bases?keyword=` 按名称或说明过滤，前端选择器输入框携带关键词刷新列表
- 2026-06-06：抽出 `hooks/knowledgeDocumentState.ts`，文档列表按 id 合并、索引状态变更提示和分页越界归一化改为纯函数
- 2026-06-06：拆分 `KnowledgePanel.tsx` 为知识库选择、文档上传和文档管理 section，并抽出文档行渲染组件
- 2026-06-06：拆分 `ApprovalAuditPanel.tsx` 为审批队列、用户状态、Token 会话和审计时间线独立 section，主面板只保留路由与权限分发
- 2026-06-06：抽出 `components/CardHeading.tsx`，知识库、问答、工单、审批、用户、会话、审计和 AI 配置主业务卡片标题统一复用
- 2026-06-06：增强 `components/ListEmpty.tsx` 支持卡片式空状态，文档管理空列表和无匹配结果复用同一组件
- 2026-06-06：抽出 `components/PaginationBar.tsx`，文档、用户、会话和审计分页统一复用同一组件
- 2026-06-06：文档管理增加每页数量选择，服务端分页请求支持 5/10/20/50 条切换并回到第一页
- 2026-06-06：审计回看升级为可过滤时间线，支持按 traceId、事件、对象和对象 ID 查询，列表可复制 traceId 并预览 payload
- 2026-06-06：回答引用和提交后的工单号支持一键复制，并通过通知反馈复制结果
- 2026-06-06：增加网络不可达失败 E2E，验证 AI 配置、知识问答和工单提交在后端断连时展示网络请求失败提示
- 2026-06-06：增加接口失败场景 E2E，覆盖文档上传校验失败、审批业务冲突和 AI 配置读取后端异常提示
- 2026-06-06：抽出 `hooks/useClipboardActions.ts`，统一处理引用、traceId、工单号复制和剪贴板不可用降级提示
- 2026-06-06：抽出 `hooks/useOperationsWorkspace.ts`，集中管理运营指标和 AI 运行配置状态刷新
- 2026-06-06：抽出 `hooks/useApprovalWorkspace.ts`，集中管理审批任务、审批备注模板和审批动作刷新
- 2026-06-06：抽出 `hooks/useAdminWorkspace.ts`，集中管理审计、用户和 Token 会话分页状态与管理动作
- 2026-06-06：抽出 `hooks/useTicketWorkspace.ts`，集中管理工单草稿、提交结果和相似工单查询状态
- 2026-06-06：抽出 `hooks/useChatWorkspace.ts`，集中管理问答结果、会话历史刷新、提问和历史恢复状态
- 2026-06-06：抽出 `hooks/useKnowledgeWorkspace.ts`，集中管理知识库选择、文档分页筛选、上传、轮询、重建、重试和删除状态
- 2026-06-06：抽出 `hooks/useAuthenticatedRequest.ts`，统一处理 access token 请求、refresh token 续期和 401 后重试
- 2026-06-06：抽出 `services/workspaceApi.ts`，集中管理前端业务接口路径、查询参数和提交 payload，`App.tsx` 回到状态编排职责
- 2026-06-06：相似工单列表展示后端返回的命中关键词和相似原因，用户可判断为什么推荐该历史工单
- 2026-06-06：增加 AI 配置页，管理员可查看 baseUrl、path、model、chat/embedding 开关、凭证配置状态和运行 readiness，页面不展示 API key
- 2026-06-06：增加生产构建集成，`npm run build` 后可用 `mvn -Pfrontend package` 将前端产物打进 Spring Boot Jar，并由后端提供 SPA fallback
- 2026-06-06：接入无新增依赖的客户端路由，知识库、问答、工单、审批、用户、会话、审计拥有独立 URL，刷新和前进后退可保持页面
- 2026-06-06：管理入口前端展示和请求守卫按 `user:admin`、`token-session:admin`、`audit:read` 细分，避免粗粒度平台权限暴露不相关操作
- 2026-06-06：文档管理接入服务端分页，文件名搜索和索引状态筛选走后端查询，支持上一页/下一页
- 2026-06-06：管理端用户状态、Token 会话和审计回看接入服务端分页，避免全量拉取或客户端截断
- 2026-06-06：运营总览展示健康等级、待处理提醒和后端建议动作，帮助管理员定位下一步处理项
- 2026-06-06：增加企业工作台首页运营指标，管理员可查看任务队列、失败索引、待审批、高风险工单和活跃会话
- 2026-06-06：增加问答历史，登录后保留当前用户最近会话并可恢复历史回答
- 2026-06-06：增加角色感知导航，普通用户不展示管理员/审批入口且不触发管理员文档接口
- 2026-06-05：增加文档搜索、索引状态筛选、失败文档批量重试和文档删除入口
- 2026-06-05：增加文档列表、后台状态轮询和重建索引入口，支持按知识库管理已上传文档
- 2026-06-04：增加文档上传状态页，上传后轮询展示 parseStatus、indexStatus、chunkCount 和失败原因
- 2026-06-04：增加登录态过期处理，后端 token 无效时自动清理本地会话并回到登录页
- 2026-06-04：增加接口错误分层展示，区分鉴权失败、校验失败、业务失败、后端异常和网络失败
- 2026-06-04：增加核心表单校验，提交前检查知识库、文件、问题、工单必填字段和负责人 ID

## P2 - 体验优化

- 引入 Tailwind CSS 或 shadcn/ui，沉淀统一 Button、Input、Card、Table、Toast 组件
- 增加空状态、骨架屏、加载中状态和重试按钮

## P3 - 产品化方向

- 增加 RAG 评测页：命中率、引用正确率、拒答率、平均响应时间
- 2026-06-06：增加审批备注模板选择和可编辑审批备注
- 增加运营指标趋势页：按时间展示任务队列、失败索引、待审批和高风险工单变化

## 技术债

- 当前文档管理已支持列表、服务端搜索、服务端状态筛选、分页大小选择、分页、轮询、重建、失败重试和删除；管理端用户/会话/审计列表已分页且前端入口已按权限细分
- 当前接口路径和 payload 已抽到 `services/workspaceApi.ts`，认证请求已抽到 `hooks/useAuthenticatedRequest.ts`，知识库/文档状态已抽到 `hooks/useKnowledgeWorkspace.ts`，问答状态已抽到 `hooks/useChatWorkspace.ts`，工单状态已抽到 `hooks/useTicketWorkspace.ts`，审批状态已抽到 `hooks/useApprovalWorkspace.ts`，审计/用户/Token 会话状态已抽到 `hooks/useAdminWorkspace.ts`，运营指标和 AI 配置运行态已抽到 `hooks/useOperationsWorkspace.ts`；后续应优先做失败场景 E2E 和组件级整理
- 当前样式是原生 CSS，适合第一版；多人协作后建议迁移到设计 token + 组件库
- 当前已有 Playwright 核心 E2E，并覆盖复制成功/剪贴板不可用降级、文档上传校验失败、审批业务冲突、AI 配置读取后端异常、AI 配置网络不可达、知识问答网络不可达和工单提交网络不可达；后续应补单元测试和更多边界失败场景 E2E
- 当前 Vite 代理默认后端端口为 `8080`，已支持通过 `VITE_API_TARGET` 覆盖；后续可补 `.env.example`
