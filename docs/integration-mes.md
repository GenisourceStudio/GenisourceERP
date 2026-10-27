# ERP ↔ MES 集成规范

Genisource ERP 与 [Genisource MES](https://github.com/genisource-mes) 是两个**独立仓库、独立部署**的产品，通过 REST API 集成。工厂可以只上 ERP、只上 MES，或两者一起上。

本文定义职责边界与接口契约。M0 只落地适配器的模块骨架；实际调用在 M5 实现。

---

## 1. 职责边界

边界原则：**ERP 管企业级计划与钱，MES 管车间执行与实物追溯。**

| 领域 | ERP 负责 | MES 负责 | 数据流向 |
|---|---|---|---|
| 物料 | 企业级主数据（采购 UOM、销售 UOM、成本价、税率、安全库存、ABC 分类） | 制造级属性（FG/SFG/RM、批次/序列号管控） | ERP → MES（按 `item_code` 对齐） |
| BOM | 计划 BOM（EBOM：成本与采购视角，多版本、替代料、损耗率） | 制造 BOM（MBOM：工序与工位视角，关键件标记） | 各自维护，`item_code` 关联；允许差异 |
| 工艺路线 | 工作中心、标准工时（用于成本与产能） | 工序、工位类型、检验点（用于执行） | ERP 引用 MES 工艺，或各自维护轻量映射 |
| 工单 | 生产订单（计划层，MRP 产出） | 执行工单（派工、报工、过站、防错） | ERP → MES 下达；MES → ERP 回传进度与完工 |
| 库存 | 仓库级库存台账（原料仓、成品仓） | 工位级 WIP、线边仓 | 完工入库由 ERP 记账；领料扣减 ERP 库存 |
| 质量 | 只读不良汇总 | 不良代码、不良判定、返修/报废 | MES → ERP（汇总） |
| 追溯 | 只读链查 | SN 履历、批次谱系、正反向追溯 | MES → ERP（查询接口） |

**为什么不做「一张工单两边都能改」**：双主会让状态机合并变成噩梦。工单的计划属性（数量、交期）归 ERP，执行属性（派工、报工）归 MES，边界清晰。

---

## 2. 认证

MES 提供 OAuth2 client credentials（M5 实现）。ERP 作为客户端：

```http
POST {MES_BASE_URL}/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id=...&client_secret=...
```

返回的 `access_token` 缓存到过期前 60 秒，失败时清空重取。

MES → ERP 方向使用 ERP 签发的服务账号 JWT（`roles=["MES_SERVICE"]`），只授予 `production:order:write`。

---

## 3. 接口契约（草案）

### 3.1 ERP → MES：下达生产订单

```http
POST {MES_BASE_URL}/api/integration/work-orders
Idempotency-Key: erp-po-{productionOrderId}
Authorization: Bearer {token}
Content-Type: application/json

{
  "erpProductionOrderId": "10086",
  "orderNo": "PO-2026-000123",
  "itemCode": "FG-1001",
  "itemName": "控制器总成",
  "plannedQty": "500",
  "uom": "PCS",
  "planStart": "2026-11-02T08:00:00+08:00",
  "planEnd": "2026-11-05T18:00:00+08:00",
  "bomVersion": "V3",
  "routingVersion": "V2",
  "priority": 5,
  "remark": "客户加急单"
}
```

响应：

```json
{ "mesWorkOrderNo": "WO-20261102-007", "status": "ACCEPTED" }
```

**幂等**：`Idempotency-Key` 相同则返回首次结果，不重复建单。ERP 侧保存 `mes_wo_no`，重试时先查再发。

### 3.2 MES → ERP：回传完工

```http
POST {ERP_BASE_URL}/api/integration/mes/completions
Idempotency-Key: mes-completion-{mesCompletionId}
Authorization: Bearer {service-token}
Content-Type: application/json

{
  "mesWorkOrderNo": "WO-20261102-007",
  "mesCompletionId": "C-90001",
  "completedQty": "480",
  "scrapQty": "12",
  "completedAt": "2026-11-05T16:20:00+08:00",
  "warehouseCode": "FG-WH1",
  "lotNo": "L20261105-01",
  "materials": [
    { "itemCode": "RM-2001", "qty": "960", "lotNo": "L20261020-03" }
  ]
}
```

ERP 侧动作（同一事务）：

1. 生成 `prd_receipt_inbound`（完工入库）
2. 增加 `inv_stock`（成品仓）
3. 按 BOM 反冲或按回传 `materials` 扣减原料
4. 生成成本凭证（M5 之后）

**幂等**：`mesCompletionId` 唯一索引，重复投递返回 200 + 原结果，不重复入库。

### 3.3 MES → ERP：进度同步（可选，低频）

```http
POST {ERP_BASE_URL}/api/integration/mes/progress
```

只更新 `prd_production_order.in_progress_qty`，不触发凭证。

### 3.4 ERP → MES：主数据同步

```http
PUT {MES_BASE_URL}/api/integration/items/{itemCode}
```

物料变更（UOM、批次管控、启停）时推送。MES 不回写物料主数据。

---

## 4. 失败处理

| 场景 | 处理 |
|---|---|
| MES 不可达 | ERP 记录 `sync_status=FAILED`，进入重试队列（退避 1min → 5min → 30min → 2h），超过 4 次告警 |
| MES 返回 409（工单已存在） | 视为成功，回写 `mes_wo_no` |
| 回传数量大于计划数量 | 拒绝并返回 422，人工核对（MES 侧超量报工应先被自身拦截） |
| 回传引用了未知工单 | 返回 404，MES 侧标记孤儿记录待人工处理 |
| 部分成功（多行物料部分失败） | 整单回滚，返回 422 + 失败明细；绝不半入库 |

---

## 5. 配置

ERP 侧 `.env`：

```bash
MES_BASE_URL=http://mes.internal:8080
MES_CLIENT_ID=genisource-erp
MES_CLIENT_SECRET=<secret>
MES_SYNC_ENABLED=true
```

`MES_SYNC_ENABLED=false` 时适配器完全休眠，ERP 独立运行——这是私有化只上 ERP 的默认形态。

---

## 6. 里程碑

| 里程碑 | 集成范围 |
|---|---|
| M0 | 模块骨架、配置项、健康检查（不发起实际调用） |
| M4 | 生产订单下达 MES（3.1）、`mes_wo_no` 回写 |
| M5 | 完工回传（3.2）、进度同步（3.3）、物料同步（3.4）、重试队列 |
