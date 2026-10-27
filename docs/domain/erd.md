# 领域模型

表前缀约定：

| 前缀 | 领域 | 里程碑 |
|---|---|---|
| `sys_` | 系统（租户、用户、角色、权限、审计） | M0 ✅ |
| `md_` | 主数据（物料、客户、供应商、BOM、工艺、工作中心） | M1 ✅ |
| `inv_` | 库存（仓库、库位、批次、库存账、出入库单） | M2 |
| `pur_` | 采购（请购、采购订单、收料、应付） | M3 |
| `sal_` | 销售（报价、销售订单、发货、应收） | M3 |
| `mrp_` | MRP（运算单、计划工单、计划采购单） | M4 |
| `prd_` | 生产订单（计划层，区别于 MES 的执行工单） | M4 |
| `fin_` | 财务（科目、凭证、总账） | M5 |

通用列：`id BIGSERIAL`、`tenant_id BIGINT`（业务表）、`created_at` / `updated_at TIMESTAMPTZ`。金额与数量一律 `NUMERIC`，不用浮点。

## M0 已实现

```mermaid
erDiagram
    sys_tenant ||--o{ sys_user : "owns"
    sys_tenant ||--o{ sys_role : "owns"
    sys_user }o--o{ sys_role : "sys_user_role"
    sys_role }o--o{ sys_permission : "sys_role_permission"
    sys_tenant ||--o{ sys_audit_log : "records"

    sys_tenant {
        bigint id PK
        varchar code UK
        varchar name
        varchar status
    }
    sys_user {
        bigint id PK
        bigint tenant_id FK
        varchar username
        varchar password_hash
        varchar display_name
        varchar status
        boolean force_password_change
        timestamptz last_login_at
        bigint version
    }
    sys_role {
        bigint id PK
        bigint tenant_id FK
        varchar code
        varchar name
        boolean builtin
    }
    sys_permission {
        bigint id PK
        varchar code UK
        varchar module
        varchar type
    }
    sys_audit_log {
        bigint id PK
        bigint tenant_id
        bigint user_id
        varchar action
        varchar resource_type
        varchar resource_id
        jsonb detail
        varchar ip_address
    }
```

要点：

- `sys_permission` 是**全局字典**，不带 `tenant_id`——权限码在所有工厂含义相同
- `sys_user_role` / `sys_role_permission` 是纯连接表，无额外列
- `sys_audit_log.tenant_id` 不设外键：审计必须能在租户重命名后存活，也不能级联删除

## M1 已实现

迁移脚本 `backend/masterdata/src/main/resources/db/migration/V3__masterdata.sql`，10 张表。

```mermaid
erDiagram
    md_uom ||--o{ md_item : "stock / purchase unit"
    md_item_category ||--o{ md_item : groups
    md_item_category ||--o{ md_item_category : "parent / child"
    md_partner ||--o{ md_item : "default supplier"

    md_item ||--o{ md_bom : "is made by"
    md_bom ||--o{ md_bom_line : contains
    md_item ||--o{ md_bom_line : "used as component"
    md_bom_line ||--o{ md_bom_substitute : "may be replaced by"
    md_item ||--o{ md_bom_substitute : "substitute item"

    md_item ||--o{ md_routing : "is produced by"
    md_routing ||--o{ md_routing_op : contains
    md_work_center ||--o{ md_routing_op : "runs on"

    md_uom {
        bigint id PK
        varchar code UK "global, no tenant_id"
        varchar name
        varchar name_en
        smallint decimals
    }
    md_item_category {
        bigint id PK
        bigint tenant_id FK
        varchar code "UK with tenant_id"
        varchar name
        bigint parent_id FK "self reference"
        integer sort_order
        varchar status
        bigint row_version
    }
    md_partner {
        bigint id PK
        bigint tenant_id FK
        varchar code "UK with tenant_id"
        varchar name
        varchar partner_type "CUSTOMER / SUPPLIER / BOTH"
        varchar tax_no
        varchar currency
        integer payment_terms_days
        numeric credit_limit
        varchar status
        bigint row_version
    }
    md_item {
        bigint id PK
        bigint tenant_id FK
        varchar code "UK with tenant_id"
        varchar name
        varchar spec
        bigint category_id FK
        varchar item_type "RAW / SEMI / FINISHED / CONSUMABLE / SERVICE"
        varchar uom_code FK
        varchar purchase_uom_code FK
        numeric purchase_factor
        boolean purchased
        integer purchase_lead_time_days
        numeric min_order_qty
        numeric safety_stock
        bigint default_supplier_id FK
        boolean sold
        numeric sale_price
        boolean manufactured
        boolean lot_controlled
        boolean serial_controlled
        integer shelf_life_days
        numeric standard_cost
        varchar currency
        numeric tax_rate
        varchar status
        bigint row_version
    }
    md_work_center {
        bigint id PK
        bigint tenant_id FK
        varchar code "UK with tenant_id"
        varchar name
        varchar department
        integer capacity_minutes_per_day
        numeric efficiency_percent
        numeric cost_rate_per_hour
        varchar status
        bigint row_version
    }
    md_bom {
        bigint id PK
        bigint tenant_id FK
        bigint item_id FK
        varchar version "UK with item_id"
        varchar status "DRAFT / RELEASED / OBSOLETE"
        numeric base_qty
        date effective_from
        date effective_to
        timestamptz released_at
        bigint row_version
    }
    md_bom_line {
        bigint id PK
        bigint tenant_id FK
        bigint bom_id FK "ON DELETE CASCADE"
        integer line_no "UK with bom_id"
        bigint component_item_id FK
        numeric qty
        numeric loss_rate
        boolean phantom
    }
    md_bom_substitute {
        bigint id PK
        bigint tenant_id FK
        bigint line_id FK "ON DELETE CASCADE"
        bigint substitute_item_id FK
        integer priority
    }
    md_routing {
        bigint id PK
        bigint tenant_id FK
        bigint item_id FK
        varchar version "UK with item_id"
        varchar status "DRAFT / RELEASED / OBSOLETE"
        timestamptz released_at
        bigint row_version
    }
    md_routing_op {
        bigint id PK
        bigint tenant_id FK
        bigint routing_id FK "ON DELETE CASCADE"
        integer seq "UK with routing_id"
        bigint work_center_id FK
        varchar name
        numeric setup_minutes
        numeric run_minutes_per_unit
    }
```

要点：

- 上图**不含** `created_at` / `updated_at`（见上文「通用列」）与纯描述性文本字段（`remark`、`short_name`、`contact_name`、`phone`、`email`、`address`）——这些字段不参与任何业务规则判断。**图中没有虚构列**，其余业务列与 `V3__masterdata.sql` 逐列一致
- **`md_uom` 是全局字典**，不带 `tenant_id`——`KG` 在每个工厂都指千克。M1 只暴露只读；自定义单位随 M2 的库存精度处理一起做，因为单位换算因子要先能配置才有意义
- **`row_version` 而非 `version`**：`md_bom` / `md_routing` 的 `version` 已经是工程版本号（`V1`/`V2`），乐观锁列再叫 `version` 就是同一个词在一张表里指两件事。所有可编辑表的乐观锁列统一叫 `row_version`
- **`md_partner` 单表**：同一家公司既可能是客户也可能是供应商，`partner_type` 用 `BOTH` 表达；按角色筛选时 `BOTH` 对两侧都可见
- **`md_item` 单表带 facet 布尔位**：把采购 / 销售 / 成本属性拆成三张从表，只会得到三张半满的表
- **`md_bom` 唯一键 `(tenant_id, item_id, version)`**，`version` 是**标签**（`V1`/`V2`/`2024-A`）不是递增数字；服务层保证同一物料最多一个 `RELEASED`
- **`md_bom_line` 只有 `loss_rate`**，没有单独的 `scrap_rate`——一个损耗率足够表达"多投一点料"，两个字段只会让人纠结该填哪个
- **`md_bom_substitute`** 独立表带 `priority`，因为替代料是"这条线位可以有 N 个备选"，塞进 `md_bom_line` 的 JSON 列就查不了
- 业务主键（`code`）在**服务层统一转大写**后再落库：PG 的唯一索引不是大小写不敏感的，靠 `upper()` 归一化比 `CITEXT` 扩展更容易迁移

### BOM 展开在哪算

当初计划用递归 CTE。实际实现放在**应用层**（`BomExplosion`），原因：

1. 展开要带**损耗率放大**与 `base_qty` 折算，还要支持 `maxLevels` 截断和环路标记——这些用递归 CTE 要拼很长的 SQL，而结果集本身是小型树
2. 同一份展开逻辑 M4 的 MRP 要复用（只换数据来源：BOM × 库存 × 在途），放 SQL 里就得复制一遍
3. 展开结果需要返回给前端做树形渲染，应用层对象本来就是目标形态

实现要点：只沿 `RELEASED` 边递归、`SCALE = 6` + `HALF_UP`、路径集合命中即标 `source = "CYCLE"`、`maxLevels` 夹在 1–20。

## M2–M5 规划（示意）

```mermaid
erDiagram
    md_item ||--o{ inv_stock : "stocked"
    inv_location ||--o{ inv_stock : holds
    inv_warehouse ||--o{ inv_location : contains
    inv_stock ||--o{ inv_lot : "lot / serial"

    md_partner ||--o{ pur_order : "supplies"
    pur_order ||--o{ pur_order_line : contains
    pur_order ||--|| pur_receipt : "received by"
    pur_receipt ||--|| inv_inbound : creates

    md_partner ||--o{ sal_order : places
    sal_order ||--o{ sal_order_line : contains
    sal_order ||--|| sal_shipment : "shipped by"
    sal_shipment ||--|| inv_outbound : creates

    sal_order ||--o{ mrp_run : triggers
    mrp_run ||--o{ mrp_plan_order : "plan WO"
    mrp_run ||--o{ mrp_plan_purchase : "plan PO"
    mrp_plan_order ||--o{ prd_production_order : releases
    prd_production_order ||--o{ prd_material_issue : issues
    prd_production_order ||--o{ prd_receipt_inbound : completes

    pur_receipt ||--|| fin_voucher : "AP"
    sal_shipment ||--|| fin_voucher : "AR"
    prd_production_order ||--|| fin_voucher : "cost"
    fin_voucher ||--o{ fin_journal_line : contains
    fin_journal_line }o--|| fin_account : posts
```

### 关键设计点

- **`inv_stock`**：按 `(tenant_id, warehouse_id, location_id, item_id, lot_id, sn)` 唯一；数量拆成 `on_hand` / `reserved` / `in_transit` / `locked`，可用量 = `on_hand - reserved - locked`
- **`mrp_run`**：快照式——记录运算参数与产出，便于复算对比；不覆盖历史
- **`prd_production_order`**：状态机 `PLANNED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED`，`mes_wo_no` 关联 MES 执行工单
- **`fin_voucher`**：`source_type` + `source_id` 反查业务源头，保证「凭证可追溯回单据」

### 库存计价

M2 采用**移动加权平均**：每次入库重算 `avg_cost = (旧金额 + 入库金额) / (旧数量 + 入库数量)`。FIFO 作为企业版可选。标准成本法依赖成本核算模块（企业版）。
