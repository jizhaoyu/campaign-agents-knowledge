# 已知问题

## 当前已知限制

- 当前代码已接入可选 Spring AI OpenAI-compatible chat，默认模式仍使用本地规则链路
- `ai-openai` profile 默认只启用 chat；如果中转站不支持 embeddings，不应开启 embedding 配置
- 当前 AI 接入使用 `/chat/completions`；若供应商只支持 Responses API，需要新增适配层或更换兼容端点
- 当前 embedding 暂存在 `embedding_json`，尚未迁移到 pgvector 原生索引
- 当前索引队列为单体内置数据库任务表，适合单机或少量实例；多实例生产部署需要进一步验证数据库锁行为和 worker 分片策略
- 当前登录 token 已改为数据库持久化 opaque session，并支持 refresh token 轮换、当前会话登出和管理员吊销
- 当前问答在模型不可用时会回退到检索驱动的规则式答案拼装
- 第一版假设文档以可直接提取文本的格式为主
- 第一版不处理复杂表格与扫描件 OCR
- 第一版审批规则较简单，后续可能需要规则引擎
- 第一版审计与指标先以“够用”为目标，不做完整运维平台

## 当前待确认项

- 当前已采用 Spring AI 作为第一阶段 AI 接入层
- ORM 采用 MyBatis-Plus 还是 JPA
- 对象存储是本地 MinIO 还是云端兼容服务
- 问答和工单是否共用一个前端工作台

## 维护约定

- 每当范围调整时，同时更新 `01-mvp-scope.md`
- 每当接口调整时，同时更新 `06-api-spec.md`
- 每当数据结构调整时，同时更新 `05-database-design.md`
