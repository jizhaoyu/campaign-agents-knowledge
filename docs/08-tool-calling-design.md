# Tool Calling 设计

## 设计目标

让 Agent 不只回答问题，还能受控地读取业务数据和生成业务动作草稿。

## 编排原则

- 读操作默认允许
- 写操作默认不直接执行
- 高风险动作必须生成草稿并进入审批
- 每次工具调用都必须写审计日志

## 工具清单

### Tool-001: searchKnowledge

用途：

- 检索知识片段

输入：

- knowledgeBaseId
- query
- topK

输出：

- 片段列表

风险级别：

- 低

### Tool-002: searchSimilarTickets

用途：

- 查询相似历史工单

输入：

- query
- topK

输出：

- 工单摘要列表

风险级别：

- 低

### Tool-003: generateTicketDraft

用途：

- 生成工单草稿

输入：

- conversationId
- question
- answer
- citations

输出：

- title
- description
- priority
- suggestedAssigneeId

风险级别：

- 中

执行规则：

- 只生成草稿，不直接开单

### Tool-004: createTicket

用途：

- 创建工单

输入：

- draft payload

输出：

- ticketId
- status

风险级别：

- 高

执行规则：

- 若命中审批规则，先创建审批任务

## Agent 决策顺序

1. 先检索知识
2. 必要时检索相似工单
3. 如用户明确要求开单，则先生成草稿
4. 用户确认后才进入创建工单动作
5. 命中审批规则则暂停执行并等待审批

## 审计要求

每次工具调用记录：

- traceId
- toolName
- actorId
- input 摘要
- output 摘要
- success/failure
- latencyMs

## 后续可扩展工具

- assignTicket
- sendNotification
- closeTicket

这些都不进入第一版。
