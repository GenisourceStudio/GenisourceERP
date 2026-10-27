# ADR-0002：共享 schema + 行级租户隔离

- 状态：Accepted
- 日期：2026-10-27

## 背景

同一套代码要同时服务两种部署形态：

1. **私有化**：一家工厂独占一套实例，数据在自己服务器上
2. **SaaS**：一套实例服务多家工厂

ERP 数据（BOM、工艺、成本价、客户名录）是工厂的核心资产，隔离出问题就是商业事故。同时 SaaS 形态要求单实例能承载几百个租户，且上线新租户不应触发 DDL。

## 决策

采用**共享 schema + `tenant_id` 行级隔离**，由 Hibernate 的 `@TenantId` 在框架层自动注入：

- 所有业务表带 `tenant_id BIGINT NOT NULL`
- 实体继承 `TenantScopedEntity`（`@TenantId` 标注）
- `TenantIdentifierResolver` 把 `TenantContext` 的当前租户交给 Hibernate
- 私有化部署固定一个租户（`ERP_TENANT_MODE=single`），SaaS 从 JWT 的 `tid` claim 取租户

业务代码**不写** `WHERE tenant_id = ?`：漏写一处就是跨租户泄漏，不能依赖人的注意力。

## 后果

**收益**

- 一份代码两种形态，私有化与 SaaS 走完全相同的查询路径，测试覆盖一次即可
- 新租户上线只是插一行 `sys_tenant`，不需要建 schema
- 运维简单：一套连接池、一次迁移、一份备份

**代价**

- 隔离强度弱于 per-schema：一条绕过 Hibernate 的原生 SQL（报表、批量修复）如果不带 `tenant_id`，就会跨租户。**约束**：原生 SQL 必须显式传租户，且 code review 重点检查
- 单租户数据量大时（>1 亿行）索引效率下降，需要按 `tenant_id` 做分区或归档。当前目标客户规模（5 年 50 万单据）远低于此阈值
- 误删/误更新影响面更大。以 `sys_audit_log` + 每日备份兜底

## 已知风险点

| 风险 | 缓解 |
|---|---|
| 原生 SQL 漏租户条件 | 原生查询集中在 `*ReportRepository`，审查清单单列；M2 起加集成测试断言跨租户不可见 |
| 后台任务无请求上下文 | `TenantContext.callWith(tenantId, ...)` 显式包裹，禁止裸跑 |
| 线程池串租户 | `TenantCleanupFilter` 在每请求 `finally` 清理 thread-local |
| 登录时租户未定 | `TenantContext.callWith` 包裹账号查询，查完即恢复 |

## 备选方案

| 方案 | 否决理由 |
|---|---|
| schema-per-tenant | PG 的 schema 数量上千后 DDL、迁移、连接池都变慢；新租户上线需要跑迁移 |
| database-per-tenant | 成本高，SaaS 形态不经济 |
| 应用层手动加 `WHERE` | 依赖人的注意力，一处遗漏就是事故 |
