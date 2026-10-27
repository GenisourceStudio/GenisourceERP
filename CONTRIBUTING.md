# 贡献指南

感谢愿意参与 Genisource ERP。这是一个面向工厂真实业务的实干型项目，我们更看重「能跑、能维护、说得清」，而不是代码量。

English speakers are welcome to open issues and pull requests in English.

## 开始之前

- 先看 [项目规划](docs/PROJECT_PLAN.md) 的「明确不做」清单。与清单冲突的功能，即使实现得很好也不会合并。
- 较大的功能（跨模块、涉及表结构、改变交互范式）请先开 issue 讨论，达成一致后再动手。
- 小修复（错别字、文档、明显的 bug）可以直接提 PR。

## 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 21 |
| Maven | 3.9+（仓库自带 wrapper） |
| Node.js | 22.12+ |
| pnpm | 10 |
| Docker | 24+（本地 PostgreSQL 与镜像构建） |

macOS 用 Homebrew 安装 JDK 21 后，若 `java -version` 仍指向旧版本，请显式指定：

```bash
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
```

中国大陆可加 Maven 镜像：

```bash
cd backend
./mvnw -s ../deploy/maven/settings-aliyun.xml verify
```

## 工作流

1. Fork 仓库，从 `main` 建分支：`feat/mrp-net-requirement`、`fix/po-status`、`docs/adr-0005`。
2. 提交信息使用 [Conventional Commits](https://www.conventionalcommits.org/)：
   - `feat: add MRP net requirement calculation`
   - `fix: reject receipt over ordered quantity`
   - `docs: clarify multi-tenant isolation`
3. **每次提交都要签署 DCO**：

   ```bash
   git commit -s -m "feat: add MRP net requirement calculation"
   ```

   这会在提交信息末尾添加 `Signed-off-by: Your Name <you@example.com>`，表示你有权按 Apache-2.0 提交该代码。
4. 推送并开 PR，描述中写清：解决什么问题、如何验证、影响哪些模块。
5. CI 通过 + 至少一名维护者 review 后合并。

## 代码规范

### 模块边界（硬规则）

后端为 Maven 多模块，依赖方向**只能自上而下**：

```
bootstrap  →  platform  →  (common)
     ↓
  masterdata  →  inventory  →  procurement / sales  →  mrp  →  production  →  finance
     ↓
  mes-integration
  ent-*（企业版增值模块，单向依赖核心，核心不得反向依赖）
```

- 禁止跨模块直接注入 Repository 或修改他模块的表；通过应用服务接口或领域事件协作。
- 核心模块**不得** import `ent-*` 包下任何类——这条由 ArchUnit 测试守护。
- 状态迁移只能通过领域服务，禁止 `setStatus(...)` 式写法。
- 金额与数量禁止使用 `double`/`float`，统一 `BigDecimal`，并显式指定 `scale` 与 `RoundingMode`。
- 所有业务表必须有 `tenant_id`，且查询必须经过租户过滤器（详见 ADR-0002）。

### 数据库

- 变更必须走 Flyway：`V{n}__snake_case.sql`。**已合并的迁移文件不可修改**，修正请新增迁移。
- 表前缀约定：`sys_` 系统 · `md_` 主数据 · `inv_` 库存 · `pur_` 采购 · `sal_` 销售 · `mrp_` MRP · `prd_` 生产订单 · `fin_` 财务。
- 所有外键、常用过滤列（`tenant_id`、`status`、`doc_date`）需要索引。
- 所有写操作要考虑幂等与并发（`Idempotency-Key`、乐观锁 `version`）。

### 后端

- Java 21，使用 record / sealed / pattern matching 等现代语法。
- 统一异常用 RFC 9457 Problem Details，错误文案同时提供中英文。
- 新增 API 必须带 springdoc 注解，出现在 `/swagger-ui.html`。

### 前端

- 函数组件 + Hooks，TypeScript `strict`，不使用 `any`。
- 数据请求统一走 TanStack Query，禁止在组件里直接 `fetch`。
- 所有用户可见文案必须走 i18n，`pnpm check:i18n` 必须通过。
- 样式使用设计系统 token（`src/styles/global.css`），不写魔法色值。

## 测试要求

| 层 | 要求 |
|---|---|
| 后端单元测试 | 领域服务与算法（MRP、成本卷积）必须有测试 |
| 后端集成测试 | 涉及 SQL / 事务的关键路径用 Testcontainers |
| 架构测试 | ArchUnit 守护模块边界 |
| 前端 | 关键页面用 Playwright E2E |

PR 中新增逻辑若无测试，维护者会要求补充后再合并。

## 提交前自检清单

```bash
cd backend && ./mvnw -q verify
cd frontend && pnpm check:i18n && pnpm build
docker compose config --quiet
```
