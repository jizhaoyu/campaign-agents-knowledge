# 错误处理设计

## 目标

- 对用户给出明确、稳定的错误反馈
- 对开发者提供足够排查信息
- 区分业务失败与系统失败

## 错误响应结构

```json
{
  "code": "RAG_NO_EVIDENCE",
  "message": "未找到足够证据支持回答",
  "data": null,
  "traceId": "abc123"
}
```

## 错误分类

### 认证与授权

- UNAUTHORIZED
- FORBIDDEN

### 参数与校验

- VALIDATION_ERROR
- UNSUPPORTED_FILE_TYPE

### 文档处理

- DOCUMENT_PARSE_FAILED
- INDEX_BUILD_FAILED

### 问答与工具

- RAG_NO_EVIDENCE（保留错误码；当前问答接口将证据不足作为 `fallback=true` 的业务成功响应返回）
- MODEL_TIMEOUT
- TOOL_CALL_FAILED

### 审批

- APPROVAL_REQUIRED
- APPROVAL_NOT_FOUND
- APPROVAL_ALREADY_DECIDED

## 重试策略

- 文档解析失败：允许后台重试
- 向量化超时：允许有限重试
- 模型接口失败：允许短重试后降级
- 审批类错误：不自动重试

## 用户提示原则

- 不暴露内部堆栈
- 明确指出下一步动作
- 对“拒答”与“系统故障”做区分
