-- ============================================================================
-- V11 — production orders: what to make, out of what, on which machines
--
-- A production order is three promises at once: a quantity of a finished item,
-- a list of the components it will eat, and the operations it will run through.
-- All three are copied from the released BOM and routing at the moment the order
-- is raised, and deliberately not re-read afterwards. A BOM revised while the
-- order is open must not silently change what the shop floor is building —
-- that is how a finished good ends up made from a bill of materials nobody
-- agreed to.
--
-- Issue and completion are not separate document tables. They are stock
-- documents whose source_type is PRD_ORDER, exactly like a goods receipt or a
-- shipment: the inventory ledger already answers "which order consumed this
-- material", and a second parallel document system would answer it differently.
-- ============================================================================

CREATE TABLE prd_production_order (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL REFERENCES sys_tenant(id),
    doc_no         VARCHAR(32)   NOT NULL,
    item_id        BIGINT        NOT NULL REFERENCES md_item(id),
    bom_id         BIGINT        REFERENCES md_bom(id),
    bom_version    VARCHAR(16),
    qty_planned    NUMERIC(18,6) NOT NULL,
    qty_completed  NUMERIC(18,6) NOT NULL DEFAULT 0,
    status         VARCHAR(16)   NOT NULL DEFAULT 'PLANNED',
    warehouse_id   BIGINT        REFERENCES inv_warehouse(id),
    location_id    BIGINT        REFERENCES inv_location(id),
    planned_start  DATE,
    planned_end    DATE,
    actual_start   TIMESTAMPTZ,
    actual_end     TIMESTAMPTZ,
    mes_wo_no      VARCHAR(64),
    source_type    VARCHAR(32),
    source_id      VARCHAR(64),
    remark         VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    created_by     VARCHAR(64),
    row_version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_prd_order_no UNIQUE (tenant_id, doc_no)
);

COMMENT ON TABLE prd_production_order IS
    'A works order: how much of an item to make, with the BOM and routing frozen at the version it was raised against.';

COMMENT ON COLUMN prd_production_order.mes_wo_no IS
    'The work order number on the MES side. Null until the order is dispatched there, which is M5.';

CREATE INDEX ix_prd_order_status ON prd_production_order (tenant_id, status);
CREATE INDEX ix_prd_order_item ON prd_production_order (tenant_id, item_id);

CREATE TABLE prd_order_line (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL REFERENCES sys_tenant(id),
    order_id      BIGINT        NOT NULL REFERENCES prd_production_order(id) ON DELETE CASCADE,
    line_no       INTEGER       NOT NULL,
    item_id       BIGINT        NOT NULL REFERENCES md_item(id),
    qty_required  NUMERIC(18,6) NOT NULL,
    qty_issued    NUMERIC(18,6) NOT NULL DEFAULT 0,
    qty_locked    NUMERIC(18,6) NOT NULL DEFAULT 0,
    warehouse_id  BIGINT        REFERENCES inv_warehouse(id),
    remark        VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_prd_order_line_no UNIQUE (order_id, line_no)
);

COMMENT ON TABLE prd_order_line IS
    'The bill of materials as the order will actually consume it, with loss already applied.';

COMMENT ON COLUMN prd_order_line.qty_locked IS
    'Stock committed to this order but not yet issued. Released the moment the issue is posted, so the issue is validated against a free quantity that still excludes the units about to be consumed.';

CREATE TABLE prd_order_operation (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT      NOT NULL REFERENCES sys_tenant(id),
    order_id        BIGINT      NOT NULL REFERENCES prd_production_order(id) ON DELETE CASCADE,
    sequence        INTEGER     NOT NULL,
    name            VARCHAR(64) NOT NULL,
    work_center_id  BIGINT      REFERENCES md_work_center(id),
    setup_minutes   NUMERIC(9,2) NOT NULL DEFAULT 0,
    run_minutes     NUMERIC(9,2) NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_prd_order_operation_seq UNIQUE (order_id, sequence)
);

COMMENT ON TABLE prd_order_operation IS
    'Operations copied from the routing at release time. M4 uses them as the work scope; dispatching them to the MES is M5.';
