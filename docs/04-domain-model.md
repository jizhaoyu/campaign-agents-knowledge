# 领域模型

## 设计原则

- 先围绕业务闭环建模，不为未来场景过度抽象
- 文档、会话、工单、审批、审计保持解耦
- Agent 作为能力层，不直接替代业务实体

## 核心实体

### User

职责：

- 表示系统用户
- 关联角色和数据权限

关键字段：

- id
- username
- displayName
- status

### Role

职责：

- 定义系统角色和权限集合

### KnowledgeBase

职责：

- 表示一个逻辑知识空间
- 控制文档归属和访问边界

### Document

职责：

- 记录原始文件、解析状态、索引状态

### DocumentChunk

职责：

- 表示可检索的最小知识片段
- 持有切片文本、位置、向量索引引用

### Conversation

职责：

- 表示一次问答会话

### Message

职责：

- 表示会话中的单条消息
- 包括用户提问、系统回答、工具结果摘要

### Ticket

职责：

- 表示工单实体
- 记录标题、描述、优先级、状态、负责人

### ApprovalTask

职责：

- 表示审批任务
- 绑定目标动作和审批结果

### AuditLog

职责：

- 记录系统行为事件
- 支持追溯和排障

## 关系概览

- User 与 Role：多对多
- KnowledgeBase 与 Document：一对多
- Document 与 DocumentChunk：一对多
- Conversation 与 Message：一对多
- Conversation 与 Ticket：一对零或一
- Ticket 与 ApprovalTask：一对多
- Conversation / Ticket / ApprovalTask 均可关联 AuditLog

## 聚合边界建议

- 知识域：KnowledgeBase, Document, DocumentChunk
- 会话域：Conversation, Message
- 工单域：Ticket, ApprovalTask
- 安全域：User, Role
- 审计域：AuditLog
