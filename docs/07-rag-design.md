# RAG 设计

## 目标

让问答结果建立在企业文档证据上，而不是仅依赖模型记忆。

## 输入范围

- 企业制度文档
- 产品说明文档
- 常见故障排查文档
- 操作手册

第一版不处理图片、扫描件 OCR、表格复杂布局。

## 当前实现状态

当前代码支持两种模式：

- 默认模式：关键词检索 + 规则式答案生成，不需要模型 API key。
- `ai-openai` profile：启用 Spring AI OpenAI-compatible chat，默认模型为 `gpt-5.4`，可通过 `OPENAI_BASE_URL` 接官方 OpenAI 或中转站。embedding 默认关闭，只有显式设置 `OPENAI_EMBEDDING_PROVIDER=openai` 和 `OPENAI_EMBEDDING_ENABLED=true` 时，导入文档才会写入 `embedding_json`，问答才会合并关键词得分和 embedding 相似度。

本机 Codex 配置中的 `base_url` 和 `model` 可以映射为项目的 `OPENAI_BASE_URL` 与 `OPENAI_CHAT_MODEL`。但 Codex 的 `wire_api = "responses"` 是客户端协议配置；当前 Spring AI 集成走 `/chat/completions`，需要供应商实际支持该接口。

当前 embedding 仍存放在业务表 JSON 字段里，目的是先打通可配置 AI 链路。后续接 PostgreSQL + pgvector 时，应迁移到原生 vector 字段和向量索引。

## 导入流程

1. 文件上传到对象存储
2. 提取文本
3. 清洗无效内容
4. 文本切片
5. 若启用 embedding，则生成向量
6. 写入切片和可选向量
7. 记录处理结果

## 切片策略

- 默认按语义段落切片
- 目标长度：400 到 800 tokens
- 重叠长度：50 到 120 tokens
- 保留文档名、章节名、页码、切片序号

## 检索策略

第一版采用混合检索：

- 关键词检索：适合制度名、报错关键字、术语
- 向量相似度：启用 `ai-openai` profile 且打开 embedding 后参与得分
- 轻量重排：按关键词分数和向量相似度加权排序

建议流程：

1. 先过滤用户有权访问的知识库
2. 关键词检索 TopK
3. 如有 query embedding，则计算切片向量相似度
4. 合并关键词分数和向量分数
5. 选择最终引用片段

## 提示词原则

- 明确要求答案只能基于提供的片段
- 要求输出引用来源
- 当证据不足时必须说明“不确定”
- 不允许编造流程、负责人、制度名称

当前实现中，ChatClient 的系统提示词已要求只能基于引用片段回答。模型调用失败时会回退到规则式答案生成。

## 引用输出格式

每条引用至少返回：

- documentId
- chunkId
- 文档名
- 片段摘要

## 拒答策略

以下场景拒答：

- 检索结果为空
- 检索命中数量低于 `app.rag.min-citation-count`
- 最高相关性分数低于 `app.rag.min-top-score`
- 片段之间互相冲突且无法判断

当前实现提供可配置强拒答门槛：

- `app.rag.min-citation-count`：默认 1，生产环境可提高到 2 或 3，要求多个引用支撑回答
- `app.rag.min-top-score`：默认 1.0，生产环境可按评测集提高，过滤弱关键词偶然命中

触发拒答时，接口仍返回 `OK`，但 `fallback=true`、`confidence=NONE`、`citations=[]`，答案文案说明未找到足够依据并提示用户补充问题。

拒答响应要求：

- 说明未找到足够依据
- 提示用户补充更具体的问题

## 评测指标

- 命中率
- TopK 召回率
- 引用正确率
- 拒答准确率
- 平均响应时间

## 未来可扩展项

- OCR
- 表格解析
- 多知识库路由
- query rewrite
- 自定义 reranker
- PostgreSQL pgvector 原生向量字段
- 离线 RAG 评测集
