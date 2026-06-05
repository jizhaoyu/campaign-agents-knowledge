# 认证与 RBAC 设计

## 目标

- 保证只有已认证用户才能访问系统
- 保证不同角色访问不同功能和数据
- 保证知识库和工单数据不被越权访问

## 角色定义

### ADMIN

权限：

- 管理用户和角色
- 管理知识库和文档
- 查看运营指标看板
- 查看全部审计日志
- 查看全部审批任务

### USER

权限：

- 发起问答
- 查看自身会话
- 生成和提交自己的工单草稿

### SUPPORT

权限：

- 查看指定范围内工单
- 查看相似工单
- 处理工单

### APPROVER

权限：

- 查看待审批任务
- 审批或驳回高风险动作

## 认证方案

- 第一版采用用户名密码登录
- 登录成功后返回 Bearer Token
- 客户端 access token 和 refresh token 都是 opaque token，不包含可解析业务载荷
- 服务端 `auth_token_session` 保存 userId、username、roleCodes、issuedAt、expiresAt、refreshExpiresAt、lastRefreshedAt 和 revokedAt
- 默认种子账号密码通过 Flyway 迁移升级为 BCrypt 哈希
- Token TTL 通过 `app.security.token-ttl-seconds` 配置，默认 7200 秒
- Refresh Token TTL 通过 `app.security.refresh-token-ttl-seconds` 配置，默认 604800 秒
- 登录失败次数通过 `app.security.max-failed-login-attempts` 配置，默认 5 次
- 临时锁定时长通过 `app.security.login-lockout-minutes` 配置，默认 15 分钟
- 登录成功、登录失败、触发或命中临时锁定、token 刷新、登出都会写审计日志

当前 token 会话已持久化到数据库，表内只保存 access token 和 refresh token 的 SHA-256 hash，不保存明文 token。应用重启后未吊销且 refresh token 未过期的会话仍可刷新；access token 过期只会拒绝受保护接口，不会直接吊销整条会话。refresh token 过期、用户登出、管理员吊销或后台清理会把会话标记为 revoked。多实例部署可共享数据库会话，但高并发下仍需压测会话查询、清理频率和数据库索引表现；如需跨系统单点登录，再考虑 JWT 签名密钥管理或集中式认证服务。

## 权限清单

后端授权使用权限字符串而不是直接绑定角色名。角色仍用于人员分组、展示和审计，登录响应会同时返回 `roles` 与 `permissions`。

| 权限 | 说明 |
| --- | --- |
| `knowledge:manage` | 创建知识库、上传/查询/重建/删除文档 |
| `chat:use` | 问答和最近问答历史 |
| `ticket:draft` | 生成工单草稿 |
| `ticket:submit` | 提交工单或提交审批 |
| `ticket:similar:read` | 查询相似工单 |
| `approval:review` | 查看审批队列、模板并审批/驳回 |
| `dashboard:read` | 查看只读运营汇总指标 |
| `audit:read` | 查询审计日志，审批队列可看全部任务 |
| `user:admin` | 查询用户和解除临时锁定 |
| `token-session:admin` | 查看和吊销 token 会话 |

## 授权检查层次

### 功能级授权

- Controller 使用 `hasAuthority('<permission>')` 做功能级授权，默认拒绝未授权访问

### 数据级授权

- 用户只能访问被授权的知识库
- 用户只能查看自身会话和自身创建工单
- 支持工程师可查看被分配范围内工单

## 权限矩阵初稿

| 功能 | ADMIN | USER | SUPPORT | APPROVER |
| --- | --- | --- | --- | --- |
| 创建知识库 | Y | N | N | N |
| 上传文档 | Y | N | N | N |
| 问答 | Y | Y | Y | Y |
| 查看相似工单 | Y | Y | Y | Y |
| 生成工单草稿 | Y | Y | Y | Y |
| 提交审批 | Y | Y | Y | N |
| 审批通过/驳回 | Y | N | N | Y |
| 查看运营看板 | Y | N | N | N |
| 查看全部审计 | Y | N | N | N |

| 角色 | 权限 |
| --- | --- |
| ADMIN | 全部管理、问答、工单、审批、运营看板和审计权限 |
| USER | `chat:use`, `ticket:draft`, `ticket:submit`, `ticket:similar:read` |
| SUPPORT | `chat:use`, `ticket:draft`, `ticket:submit`, `ticket:similar:read` |
| APPROVER | `chat:use`, `ticket:similar:read`, `approval:review` |

## 安全要求

- 密码仅保存哈希
- token 明文不落库，仅保存 hash
- 连续登录失败后临时锁定账号，降低暴力破解风险
- 接口统一鉴权
- 审计日志禁止普通用户修改
- 关键操作写入 actorId 和 traceId；认证审计 payload 不得包含密码、明文 token 或 token hash
- 认证失败返回 401
- 授权失败返回 403，不应被全局异常处理吞成 500
