# 文档

| 文档 | 内容 |
|---|---|
| [PROJECT_PLAN.md](PROJECT_PLAN.md) | 项目规划：定位、差异化、Open Core 边界、M0–M5 路线图、风险 |
| [design-system.md](design-system.md) | 设计系统：品牌 token、字体、组件、无障碍、响应式 |
| [domain/erd.md](domain/erd.md) | 领域模型：M0/M1/M2 已实现表结构与 M3–M5 规划、关键设计点 |
| [integration-mes.md](integration-mes.md) | 与 Genisource MES 的职责边界、接口契约、失败处理 |
| [adr/](adr/README.md) | 架构决策记录（ADR-0001 ~ 0005） |

## 面向不同读者

**想部署试用的工厂**：先读根目录 [README](../README.md) 的「快速开始」，运行 `./deploy/scripts/install.sh`。

**想二次开发的工程师**：读 [PROJECT_PLAN](PROJECT_PLAN.md) 的「明确不做」→ [ADR](adr/README.md) → [CONTRIBUTING](../CONTRIBUTING.md)。

**想了解领域模型的实施顾问**：读 [domain/erd.md](domain/erd.md) 与 [integration-mes.md](integration-mes.md)。

## 关于文档站

当前文档是仓库内的 Markdown，尚未搭建 Docusaurus 站点。M0 优先把系统跑起来，M1 优先把主数据跑通；文档站等出现「版本化 / 搜索 / 中英切换」的真实需求时再建——现在建站只是把 Markdown 换个皮，反而多一处要同步的地方。
