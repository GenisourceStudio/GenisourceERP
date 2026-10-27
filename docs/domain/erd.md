# 领域模型

表前缀约定：

| 前缀 | 领域 | 里程碑 |
|---|---|---|
| `sys_` | 系统（租户、用户、角色、权限、审计） | M0 ✅ |
| `md_` | 主数据（物料、客户、供应商、BOM、工艺、工作中心） | M1 |
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

## M1–M5 规划（示意）

```mermaid
erDiagram
    md_item ||--o{ md_bom : "plan BOM"
    md_bom ||--o{ md_bom_line : contains
    md_bom_line }o--|| md_item : component
    md_bom_line ||--o{ md_bom_substitute : "substitutes"
    md_item ||--o{ md_routing : "routing"
    md_routing ||--o{ md_routing_op : contains
    md_routing_op }o--|| md_work_center : "runs on"

    md_item ||--o{ inv_stock : "stocked"
    inv_location ||--o{ inv_stock : holds
    inv_warehouse ||--o{ inv_location : contains
    inv_stock ||--o{ inv_lot : "lot / serial"

    md_supplier ||--o{ pur_order : receives
    pur_order ||--o{ pur_order_line : contains
    pur_order ||--|| pur_receipt : "received by"
    pur_receipt ||--|| inv_inbound : creates

    md_customer ||--o{ sal_order : places
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

- **`md_bom` 多版本**：`(item_id, version)` 唯一，状态 `DRAFT / RELEASED / OBSOLETE`；只有 `RELEASED` 参与 MRP 展开
- **`md_bom_line`**：含 `qty`、`loss_rate`、`scrap_rate`、`is_phantom`（虚拟件）；替代料放独立表 `md_bom_substitute`，带优先级
- **`inv_stock`**：按 `(tenant_id, warehouse_id, location_id, item_id, lot_id, sn)` 唯一；数量拆成 `on_hand` / `reserved` / `in_transit` / `locked`，可用量 = `on_hand - reserved - locked`
- **`mrp_run`**：快照式——记录运算参数与产出，便于复算对比；不覆盖历史
- **`prd_production_order`**：状态机 `PLANNED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED`，`mes_wo_no` 关联 MES 执行工单
- **`fin_voucher`**：`source_type` + `source_id` 反查业务源头，保证「凭证可追溯回单据」

### 库存计价

M2 采用**移动加权平均**：每次入库重算 `avg_cost = (旧金额 + 入库金额) / (旧数量 + 入库数量)`。FIFO 作为企业版可选。标准成本法依赖成本核算模块（企业版）。
