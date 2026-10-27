# Genisource ERP

面向国内中小制造业的**开源 ERP**：以 MRP 运算为内核，与 [Genisource MES](https://github.com/genisource-mes) 组成「计划层 + 执行层」的完整制造业套件——采购、销售、库存、BOM、MRP 算得清，车间执行管得住。

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![React](https://img.shields.io/badge/React-19-149eca)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791)

> **当前状态：M0 脚手架（进行中）。** 本阶段交付可运行的骨架、多租户基座、RBAC、Flyway 基线与部署编排。
> 业务模块（主数据 / 库存 / 采购 / 销售 / MRP / 生产订单 / 财务）按 [项目规划](docs/PROJECT_PLAN.md) 的 M1–M5 逐片交付。
> **生产环境请勿使用当前版本。**

## 这是什么 / 不是什么

**是**：面向 50–500 人离散制造企业（电子装配、机加工、注塑、五金、小家电）的开源 ERP。核心是「BOM 算得准、MRP 算得快、采购销售库存财务一本账」，并且能与 MES 打通。

**不是**：全行业通用 ERP、完整 APS 高级排程、WMS 立库调度、低代码平台、财务公司级总账系统。边界写在 [项目规划 §2](docs/PROJECT_PLAN.md)，并且会一直守住。

## 与 Genisource MES 的关系

```
┌──────────────────────────────┐         ┌──────────────────────────────┐
│      Genisource ERP          │  REST   │      Genisource MES          │
│  计划层 · 企业资源计划        │◄───────►│  执行层 · 制造执行           │
│                              │ Webhook │                              │
│  采购 · 销售 · 库存 · 财务    │         │  派工 · 报工 · 防错 · 追溯    │
│  BOM(EBOM) · 工艺 · MRP      │         │  BOM(MBOM) · 工单 · 工位      │
└──────────────────────────────┘         └──────────────────────────────┘
```

两者是**独立仓库、独立部署**的两个产品，通过标准化 REST API 集成：ERP 把生产订单下达到 MES，MES 把完工与用料回传给 ERP。工厂可以只上 MES，也可以只上 ERP，还可以一起上。集成规范见 [docs/integration-mes.md](docs/integration-mes.md)。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21 · Spring Boot 4.1 · Spring Data JPA · Spring Security · Flyway |
| 前端 | React 19 · TypeScript · Vite · TanStack Query · React Router 7 · i18next |
| 数据库 | PostgreSQL 17 |
| 缓存 | Redis |
| 工作流 | Flowable 7（M3 接入） |
| 报表 | 积木报表（M5 接入） |
| 部署 | Docker Compose（私有化）· Helm（SaaS） |
| 设计系统 | 「生产控制台」体系，与 Genisource MES 统一品牌 |

**架构形态**：模块化单体（Modular Monolith）。中小厂部署简单、事务一致性强；未来高负载模块（MRP 运算）可单独抽出。

## 快速开始

要求：Docker 24+（含 Compose v2），4 GB 内存。首次启动会拉取镜像并下载依赖，约 3–10 分钟。

```bash
git clone <this-repo> genisource-erp
cd genisource-erp
./deploy/scripts/install.sh
```

完成后访问：

- 管理后台：<http://localhost:8080>
- API 文档：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>

中国大陆网络可使用国内镜像：

```bash
./deploy/scripts/install.sh --cn-mirror
```

## 本地开发

后端（JDK 21，Maven 使用仓库自带 wrapper）：

```bash
cd backend
./mvnw spring-boot:run
```

> **macOS 提示**：如果 `./mvnw` 报 `The forked VM terminated without properly saying goodbye`，通常是 `JAVA_HOME` 没设、Maven 抓到了系统里的旧 JDK（例如 Oracle 的 Java 8）。显式指定即可：
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 21)          # 系统安装的 JDK 21
> export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home   # Homebrew 安装的
> ```
> Homebrew 装的 JDK 不会被 `/usr/libexec/java_home` 索引，除非建过软链：
> ```bash
> sudo ln -sfn $(brew --prefix openjdk@21)/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
> ```
> 验证：`$JAVA_HOME/bin/java -version` 输出 `21.x` 再继续。

前端（Node 22.12+，pnpm 10）：

```bash
cd frontend
pnpm install
pnpm dev
```

前端开发服务器监听 `5173`，并把 `/api` 与 `/actuator` 代理到 `8080`；生产环境由 Nginx 完成同样的转发。

## 仓库结构

```
backend/
  platform/      平台基座：多租户、安全、审计、通用
  masterdata/    主数据：物料、客户/供应商、BOM、工艺路线、工作中心
  inventory/     库存：仓库库位、批次、库存余额、出入库单据、库存账
  bootstrap/     启动器：配置、Flyway 迁移、演示数据
frontend/        React SPA
deploy/          Dockerfile、Nginx、Maven 镜像、K8s Helm、运维脚本
docs/            项目规划、ADR、领域模型、集成规范
```

## 路线图

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 脚手架 · 多租户 · RBAC · 部署编排 | ✅ |
| M1 | 主数据 · BOM（多级/版本/替代料） · 工艺路线 | ✅ |
| M2 | 库存（多仓/批次/序列号/盘点） | ✅ |
| M3 | 采购到付款 · 销售到收款 · 审批流 | ⏳ |
| M3 | 采购到付款 · 销售到收款 · 审批流 | ⏳ |
| M4 | MRP 运算 · 生产订单 | ⏳ |
| M5 | 财务（凭证/总账/报表） · MES 集成 · v1.0 | ⏳ |

完整规划见 [docs/PROJECT_PLAN.md](docs/PROJECT_PLAN.md)。

## Open Core

核心模块永久开源（Apache-2.0），商业化依靠增值模块与服务：

| 开源核心 | 企业版增值模块 |
|---|---|
| 主数据 · 库存 · 采购 · 销售 · MRP · 生产订单 · 财务（简化）· 平台基座 | 成本核算 · APS 排程 · QMS · EAM · 多组织 · 高级财务（多币种/金税）· 移动端 · BI · SLA |

## 参与贡献

欢迎！请先读 [贡献指南](CONTRIBUTING.md)。所有提交需签署 DCO（`git commit -s`）。

## 许可

[Apache License 2.0](LICENSE)。详见 [NOTICE](NOTICE)。
