# 架构决策记录（ADR）

短小的、只增不改的记录。每条回答三个问题：当时面对什么约束、选了什么、接受了什么代价。

**已合并的 ADR 不修改。** 决策改变时新增一条，并在旧条目顶部标注 `Superseded by ADR-XXXX`。

| 编号 | 标题 | 状态 |
|---|---|---|
| [0001](0001-modular-monolith.md) | 采用模块化单体而非微服务 | Accepted |
| [0002](0002-row-level-multi-tenancy.md) | 共享 schema + 行级租户隔离 | Accepted |
| [0003](0003-stateless-jwt-auth.md) | 无状态 JWT 认证 | Accepted |
| [0004](0004-maven-multi-module-open-core.md) | Maven 多模块承载 Open Core 边界 | Accepted |
