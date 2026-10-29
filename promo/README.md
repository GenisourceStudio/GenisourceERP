# Genisource ERP · 产品宣传片

成片：`genisource-erp-promo.mp4` — 1920×1080 / 30fps / 104s / H.264 + AAC。

## 内容结构

| 时间 | 段落 | 画面 |
|---|---|---|
| 00:00 | 现状 | Excel 排产 / 微信催料 / 月底凑账，三个失效点 + 「这批料，到底还缺多少？」 |
| 00:05 | 品牌 | Genisource ERP + 一条链五步（需求 → 净需求 → 供应 → 过账 → 凭证）+ 四个交付指标 |
| 00:11 | 三件事 | 算得清 / 走得通 / 记得住 |
| 00:15 | 01 主数据 | BOM 列表：多版本并存，发布即锁定 |
| 00:23 | 02 需求运算 | MRP 补货建议表（9 项缺料 · 9 张建议单 · 25 个物料） |
| 00:31 | 净需求算式 | 960 + 1,200 − 1,000 = 1,160，以及 2,951.72 → 3,000 PCS 的取整 |
| 00:38 | 03 转单 | 生产订单 / 采购订单双屏：建议点一下就成单 |
| 00:46 | 04 库存 | 库存余额 + 库存流水：过账才动账，流水只追加 |
| 00:54 | 05 购销 | 销售订单 + 行记忆：已执行量自己会累 |
| 01:01 | 06 财务 | 试算平衡表：过账即记账，借贷必须平 |
| 01:09 | 07 集成 | ERP ⇄ MES 三步流程 + 发件箱空态截图 |
| 01:17 | 08 交付 | 安装脚本终端输出 + 四个容器健康状态 |
| 01:26 | 收尾 | 里程碑 M0–M5 + 启动命令 + v1.0.0 印章 |
| 01:39 | 尾板 | Genisource ERP · PLAN · POST · BALANCE |

## 素材与事实来源

- 截图全部取自本机实际运行的实例（前端 `127.0.0.1:5175` + 后端 `8081`，demo profile），`node tools/capture.mjs` 一键重采，未做 UI 重绘。
- 两张「宽幅数据条」（`mrp-plans.png`、`finance-trial.png`）是页面内表格元素的裁切，用于把 14px 的表格文字放大到约 18px，保证 1080p 下可读。
- 片中的每个数字都来自 demo 数据本身，可在对应页面核对：MRP-20261029-0008 的 9 项缺料、FG-TC220 的净需求 1,160、RM-PCB 的 2,951.72 → 3,000、应收 562,704.07 / 应付 261,408.55、试算平衡表末行「借贷平衡」。
- 文案只写已交付的 M0–M5 能力（见 README 路线图与 `docs/PROJECT_PLAN.md`），企业版能力（成本核算、APS、QMS、多组织、多币种）不出现在片子里。
- 视觉语言沿用 `docs/design-system.md`：暖纸底 + 32px 网格 + 墨黑 + 国际橙，Archivo 变量字体与 DM Mono 均来自 `frontend` 的自托管字体。

## 重新生成

```bash
cd promo
# 依赖：本机 Chrome（channel: chrome）、node、ffmpeg（本机在 /opt/homebrew/bin）
# playwright 从同级项目软链过来即可，不必重新下载浏览器：
mkdir -p node_modules && ln -sfn ../../doooot/node_modules/playwright node_modules/playwright

# 1. 采图（要求后端 8081、前端 5175 已启动）
node tools/capture.mjs                 # 25 张整页截图 -> assets/
node tools/capture-strips.mjs          # 2 张宽幅表格裁切 -> assets/

# 2. 自检（两套，缺一不可）
node tools/render.mjs audit            # 全时间轴：文字溢出 / 出框
node tools/check.mjs                   # 全时间轴：文字互相压盖 / 小字号

# 3. 渲染与合成
node tools/render.mjs frames           # 3132 帧，约 5–8 分钟
bash tools/encode.sh                   # 首次会自动合成配乐，再合成 mp4
```

改文案直接改 `storyboard.html`：每个场景用 `data-start / data-end` 定时间窗，元素用 `--d`（绝对秒）定入场时刻；`window.seek(t)` 会把所有 CSS 动画定格到第 t 秒，所以渲染是逐帧确定、可复现的。

`tools/pixels.mjs <dir> [x,y,w,h]` 可以统计任意帧的亮度分布——这是"看不见画面时的自检手段"：暗场标题区应当很暗（白字才读得清），截图区应当偏亮（UI 确实画出来了）。

## 与 MES 宣传片的关系

两者共用一套版式语汇与渲染管线（帧定格、程序化配乐、ffmpeg 合成），但内容结构不同：MES 讲「工单 → 扫码 → 防错 → 报工 → 追溯」的执行闭环，ERP 讲「需求 → 净需求 → 供应 → 过账 → 凭证」的计划与账务链。ERP 多了一场「净需求算式」，因为"算得清"是这个产品最该被看见的东西。
