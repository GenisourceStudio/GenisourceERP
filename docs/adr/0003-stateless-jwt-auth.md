# ADR-0003：无状态 JWT 认证

- 状态：Accepted
- 日期：2026-10-27

## 背景

需要一套能在单机私有化和多实例 SaaS 下都成立、且能承载多租户上下文的认证方案。ERP 用户是工厂员工，一天登录一次、连续使用 8 小时，不是高频登录场景，但**权限变更需要及时生效**（离职、调岗）。

## 决策

使用 HMAC-SHA256 签名的**无状态 JWT**，不引入服务端 session 存储：

- Access token 有效期 15 分钟，Refresh token 7 天
- Token 携带：`sub`（用户 id）、`tid`（租户 id）、`username`、`name`、`roles`、`perms`
- 授权在 token 解析阶段完成，不查库
- 租户在 `JwtAuthenticationConverter` 中写入 `TenantContext`，早于任何 Repository 调用

## 后果

**收益**

- 实例无状态，SaaS 水平扩展不需要粘性会话或共享 session 存储
- 权限与租户随 token 传递，一次解析即可授权，避免每请求多次查库
- 多租户不需要额外的会话隔离机制

**代价**

- **撤销延迟**：改权限后最长 15 分钟才生效（access token 过期）。对 ERP 可接受；紧急场景（离职）通过轮换 `ERP_JWT_SECRET` 让全部会话立即失效
- Token 体积随权限数量增长。当前 27 个权限约 1.5 KB，可接受；超过 100 个权限时应改为「token 只带角色，权限查缓存」
- Refresh token 无法单独吊销（无状态）。M1 引入 Redis 后加 `jti` 黑名单，把吊销延迟压到 0

## 关键约束

- `ERP_JWT_SECRET` 至少 32 字节，启动时校验，不足直接拒绝启动
- 生产部署严禁使用默认 secret；`install.sh` 生成随机值
- Token 中的 `tid` 一旦签发不可篡改（签名保护），因此租户不可能通过改请求头越权

## 备选方案

| 方案 | 否决理由 |
|---|---|
| 服务端 session（Spring Session + Redis） | 增加一个必须高可用的组件；SaaS 扩展要处理 session 复制 |
| OAuth2 + 外部 IdP（Keycloak） | 中小厂没有 IdP 运维能力，部署复杂度与收益不匹配 |
| 长期 token（不刷新） | 泄露风险窗口过大 |
