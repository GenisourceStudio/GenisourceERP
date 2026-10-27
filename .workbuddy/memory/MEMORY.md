# Genisource ERP — 项目长期记忆

## 定位
面向国内中小制造业（50–500 人，离散制造）的开源 ERP。Apache-2.0，Open Core 商业化。
与姊妹项目 **Genisource MES**（`../GenisourceMES`，v0.1.0-alpha 已发布）组成「计划层 + 执行层」套件，两仓库独立部署，走 REST API 集成。

## 技术栈（已定，与 MES 对齐）
- 后端：Java 21 + Spring Boot **4.1.2** + Spring Data JPA + Flyway + PostgreSQL 17
- 前端：React 19 + Vite 8 + TS + TanStack Query 5 + Zustand 5 + React Router 7 + i18next
- 架构：模块化单体；**Maven 多模块**（`platform` / `bootstrap`，后续 `masterdata`/`inventory`/... /`ent-*`）
- 多租户：共享 schema + `@TenantId` 行级隔离（ADR-0002）
- 认证：无状态 JWT HS256，15min access / 7d refresh（ADR-0003）

## 服务限制（跨项目提示）
- 本机无系统级 Java 21，用 `/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- Maven 系统版本 3.8.1 过旧 → 一律用仓库内 `./mvnw`（3.9.11）
- **8080 已被 Genisource MES 占用** → ERP 后端用 **8081**
- 5174 被其他 Vite 项目占用（IPv6/IPv4 双绑）→ ERP 前端用 **5175**，且必须 `host: '127.0.0.1'` + `strictPort: true`（Node 把 localhost 解析成 ::1）
- 本地验证用容器 `erp-pg-dev`（postgres:17-alpine，5432，db=genisource_erp / erp / erp_dev_password）
- **Docker 构建陷阱**：`~/.docker/config.json` 里 Docker Desktop 配了代理 `http://192.168.31.205:7890`（ClashX），容器内不可达 → npm 构建失败 `ECONNREFUSED 192.168.31.205:7890`。解法：构建时加 `--build-arg HTTP_PROXY= --build-arg HTTPS_PROXY= --build-arg http_proxy= --build-arg https_proxy=`
- **buildx 被沙箱拦截**（写 `~/.docker/buildx` 被拒）→ 本机验证用 `DOCKER_BUILDKIT=0 docker build`

## Spring Boot 4 的破坏性变更（踩过的坑）
1. **Jackson 3**：包名 `tools.jackson.*`，`JacksonException` 已改为 **unchecked**（extends RuntimeException）。不要再 import `com.fasterxml.jackson.*`。
2. `HibernatePropertiesCustomizer` 迁到 `org.springframework.boot.hibernate.autoconfigure`
3. `MacAlgorithm` 在 `org.springframework.security.oauth2.jose.jws`
4. Hibernate 7 **删除了** `MultiTenancyStrategy` / `MULTI_TENANCY_STRATEGY`；`@TenantId` 会自动启用 discriminator，只需注册 `CurrentTenantIdentifierResolver`
5. `TenantRegistry` 作为 ApplicationRunner 必须 `@Order(0)`，否则 `InitialAdminCreator(@Order(10))` 先跑，拿到 `UNRESOLVED(-1)` 租户

## 关键接口约定
- 错误响应：RFC 9457 ProblemDetail + `code` 字段（`urn:genisource:erp:error:<code>`）
- i18n：后端 `i18n/messages*.properties`（默认中文）；前端 `zh-CN.json`/`en-US.json` 必须 key 对齐（CI 校验）
- 表前缀：`sys_ md_ inv_ pur_ sal_ mrp_ prd_ fin_`
- 默认管理员由 `InitialAdminCreator` 在首次启动时按 `ERP_ADMIN_PASSWORD` 创建（不写死在 SQL 里）

## 里程碑
M0 ✅（脚手架/多租户/JWT+RBAC/部署/CI/文档）→ M1 主数据+BOM+工艺 → M2 库存 → M3 购销 → M4 MRP+生产订单 → M5 财务+MES 集成+v1.0
