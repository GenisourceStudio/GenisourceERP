# ADR-0004：Maven 多模块承载 Open Core 边界

- 状态：Accepted
- 日期：2026-10-27

## 背景

项目采用 Open Core 模式：核心永久开源（Apache-2.0），企业版增值模块单独授权。这要求两条硬约束：

1. 增值模块能**独立编译、独立打包、按 License 加载**，不必把商业代码发布到开源仓库
2. 核心模块**绝不能**依赖增值模块，否则开源仓库无法独立构建

姊妹项目 Genisource MES 是单 Maven 模块（纯开源，无此需求），ERP 不能照抄。

## 决策

后端采用 **Maven 多模块**，依赖方向单向：

```
bootstrap  →  platform
bootstrap  →  业务模块（masterdata / inventory / procurement / sales / mrp / production / finance / mes-integration）
业务模块    →  platform         （同级模块之间通过应用服务接口，不直接依赖）
ent-*      →  核心模块          （单向，企业版可依赖核心）
核心模块    →  ✗ ent-*          （禁止，ArchUnit 守护）
```

- 开源仓库包含 `bootstrap` / `platform` / 业务模块
- 企业版模块 `ent-*` 在私有仓库，通过 Maven 私服发布，`bootstrap` 以 `provided` 或 profile 方式引入
- 加载条件：`@ConditionalOnProperty(prefix="erp.enterprise", name="<module>.enabled")` + License 校验（签名 JWT）
- `ModuleBoundaryTest`（ArchUnit）断言：`com.genisource.erp.platform..` 与核心业务包不得 import `com.genisource.erp.ent..`

## 后果

**收益**

- 开源仓库可独立构建、独立发布，不泄漏商业代码
- 企业版可以按客户单独授权（只发对应模块的 jar）
- 模块边界在编译期就有约束，比包级约定强得多

**代价**

- 构建复杂度上升：`bootstrap` 依赖全部业务模块，任一模块编译失败都会阻断整体构建
- 本地开发需要理解模块依赖方向；已在 CONTRIBUTING 中写明
- 企业版模块的开发需要私服与本地 profile 配置

## 备选方案

| 方案 | 否决理由 |
|---|---|
| 单模块 + 包级分区（照抄 MES） | 企业版代码无法独立发布；开源仓库要么含商业代码，要么无法构建 |
| 独立微服务承载企业版 | 引入服务治理成本；企业版功能（成本核算、APS）与核心共享大量实体 |
| 多仓库拆分（core / enterprise） | 核心模块之间仍需共享实体与事务，跨仓库版本协调成本高 |
