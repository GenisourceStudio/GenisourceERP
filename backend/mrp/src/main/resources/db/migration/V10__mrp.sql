-- ============================================================================
-- V10 — MRP: one run, the evidence behind it, and the plan it recommends
--
-- A run is a snapshot. It records the parameters it was given and the numbers
-- it derived, and is never overwritten: next week's planner needs to be able to
-- ask "why did we buy three hundred of these in October", and a table that
-- holds only the latest answer cannot tell them.
--
-- Three tables, not five. The plan sketch had mrp_plan_order and
-- mrp_plan_purchase side by side; they are the same thing — a quantity of an
-- item that somebody has to obtain — differing only in whether a supplier or
-- the shop floor does the obtaining. One table with a supply_type discriminator,
-- the same shape pur_document and sal_document already use.
--
-- Timestamps follow the rest of the schema. A run and its demand lines are
-- written once and never touched, so they carry created_at and no updated_at;
-- a plan row does change — it becomes a purchase or works order — so it keeps
-- both.
-- ============================================================================

CREATE TABLE mrp_run (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT      NOT NULL REFERENCES sys_tenant(id),
    run_no         VARCHAR(32) NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    horizon_days   INTEGER     NOT NULL DEFAULT 30,
    demand_from    DATE,
    demand_to      DATE,
    include_safety BOOLEAN     NOT NULL DEFAULT TRUE,
    max_levels     INTEGER     NOT NULL DEFAULT 12,
    item_count     INTEGER     NOT NULL DEFAULT 0,
    supply_count   INTEGER     NOT NULL DEFAULT 0,
    shortage_count INTEGER     NOT NULL DEFAULT 0,
    run_by         VARCHAR(64),
    remark         VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_mrp_run_no UNIQUE (tenant_id, run_no)
);

COMMENT ON TABLE mrp_run IS
    'One MRP calculation: its parameters, its totals, and when it was run. Append-only; the plan rows hang off it. created_at is the run time — a second run_at column would hold the same instant.';

CREATE INDEX ix_mrp_run_date ON mrp_run (tenant_id, created_at DESC);

-- Why each item arrived on the list. Without this a planner sees "buy 300" and
-- has no way to tell an exploding sales order from a safety-stock top-up, so
-- every number is kept, including the ones that summed to nothing.
CREATE TABLE mrp_demand (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL REFERENCES sys_tenant(id),
    run_id          BIGINT        NOT NULL REFERENCES mrp_run(id) ON DELETE CASCADE,
    item_id         BIGINT        NOT NULL REFERENCES md_item(id),
    item_level      INTEGER       NOT NULL DEFAULT 0,
    gross_qty       NUMERIC(18,6) NOT NULL,
    on_hand         NUMERIC(18,6) NOT NULL DEFAULT 0,
    reserved_qty    NUMERIC(18,6) NOT NULL DEFAULT 0,
    locked_qty      NUMERIC(18,6) NOT NULL DEFAULT 0,
    in_transit      NUMERIC(18,6) NOT NULL DEFAULT 0,
    safety_stock    NUMERIC(18,6) NOT NULL DEFAULT 0,
    net_qty         NUMERIC(18,6) NOT NULL DEFAULT 0,
    required_date   DATE,
    from_sales      NUMERIC(18,6) NOT NULL DEFAULT 0,
    from_components NUMERIC(18,6) NOT NULL DEFAULT 0,
    shortage        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_mrp_demand_item UNIQUE (run_id, item_id)
);

COMMENT ON TABLE mrp_demand IS
    'Per run, per item: gross demand split by where it came from, what could be netted against it, and what is left over.';

COMMENT ON COLUMN mrp_demand.reserved_qty IS
    'Stock promised to sales orders that fall OUTSIDE the planning window. Promises inside the window cancel out — the units are in on_hand and the order is in gross_qty — so they are not recorded here; adding them would net the same requirement twice.';

CREATE INDEX ix_mrp_demand_run ON mrp_demand (run_id, shortage);

-- What the run recommends obtaining. Deliberately separate from the demand
-- table: a shortage is a fact about the past, a plan row is a decision that can
-- be turned into a purchase order or a works order and then reports back.
CREATE TABLE mrp_plan_supply (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL REFERENCES sys_tenant(id),
    run_id         BIGINT        NOT NULL REFERENCES mrp_run(id) ON DELETE CASCADE,
    item_id        BIGINT        NOT NULL REFERENCES md_item(id),
    supply_type    VARCHAR(16)   NOT NULL,
    qty            NUMERIC(18,6) NOT NULL,
    net_qty        NUMERIC(18,6) NOT NULL,
    order_multiple NUMERIC(18,6) NOT NULL DEFAULT 1,
    supplier_id    BIGINT        REFERENCES md_partner(id),
    lead_time_days INTEGER       NOT NULL DEFAULT 0,
    start_date     DATE,
    due_date       DATE,
    status         VARCHAR(16)   NOT NULL DEFAULT 'PLANNED',
    converted_type VARCHAR(32),
    converted_id   VARCHAR(64),
    converted_no   VARCHAR(32),
    remark         VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_mrp_plan_supply UNIQUE (run_id, item_id)
);

COMMENT ON TABLE mrp_plan_supply IS
    'What MRP recommends obtaining, one row per item per run. Becoming a real order flips the status and records the document it became.';

COMMENT ON COLUMN mrp_plan_supply.qty IS
    'What to obtain, rounded up to the item''s order multiple. net_qty keeps the raw shortfall so the rounding can be seen.';

CREATE INDEX ix_mrp_plan_supply_run ON mrp_plan_supply (run_id, status);
