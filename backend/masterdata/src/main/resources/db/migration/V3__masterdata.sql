-- ============================================================================
-- V3 — master data
--
-- Owned by the masterdata module; the file lives in that module's classpath so a
-- standalone deployment of a commercial module can ship its own migrations without
-- touching this one.
--
-- Reading order for the tables below:
--   md_uom              global unit dictionary (not tenant scoped, see note)
--   md_item_category    item grouping, optional one level of nesting
--   md_item             the item master, carrying purchasing / sales / costing facets
--   md_partner          customers and suppliers in one table
--   md_work_center      capacity and cost rate of a production resource
--   md_bom / _line / _substitute    multi-level bill of materials with versions
--   md_routing / _op    operations per item, each bound to a work centre
-- ============================================================================

-- Units are a global dictionary, like sys_permission: PCS means the same thing in
-- every factory. M1 exposes it read-only; custom units arrive together with inventory
-- precision handling in M2, because a custom unit is only meaningful once conversion
-- factors are settable.
CREATE TABLE md_uom (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(16)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    name_en     VARCHAR(64)  NOT NULL,
    decimals    SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_uom_code UNIQUE (code)
);

INSERT INTO md_uom (code, name, name_en, decimals) VALUES
    ('PCS', '个',   'Piece',      0),
    ('SET', '套',   'Set',        0),
    ('KG',  '千克', 'Kilogram',   3),
    ('G',   '克',   'Gram',       3),
    ('T',   '吨',   'Tonne',      3),
    ('M',   '米',   'Metre',      3),
    ('MM',  '毫米', 'Millimetre', 3),
    ('M2',  '平方米', 'Square metre', 3),
    ('M3',  '立方米', 'Cubic metre',  3),
    ('L',   '升',   'Litre',      3),
    ('BOX', '箱',   'Box',        0),
    ('ROLL','卷',   'Roll',       3);

CREATE TABLE md_item_category (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL REFERENCES sys_tenant (id),
    code       VARCHAR(32)  NOT NULL,
    name       VARCHAR(128) NOT NULL,
    parent_id  BIGINT       REFERENCES md_item_category (id),
    sort_order INTEGER      NOT NULL DEFAULT 0,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_item_category_code UNIQUE (tenant_id, code)
);
CREATE INDEX ix_md_item_category_tenant ON md_item_category (tenant_id);
CREATE INDEX ix_md_item_category_parent ON md_item_category (parent_id);

CREATE TABLE md_partner (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL REFERENCES sys_tenant (id),
    code              VARCHAR(32)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    short_name        VARCHAR(64),
    partner_type      VARCHAR(16)  NOT NULL,
    tax_no            VARCHAR(64),
    contact_name      VARCHAR(64),
    phone             VARCHAR(32),
    email             VARCHAR(128),
    address           VARCHAR(255),
    currency          VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    payment_terms_days INTEGER     NOT NULL DEFAULT 0,
    credit_limit      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    remark            VARCHAR(500),
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    row_version       BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_partner_code UNIQUE (tenant_id, code)
);
CREATE INDEX ix_md_partner_tenant_type ON md_partner (tenant_id, partner_type);

CREATE TABLE md_item (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              BIGINT       NOT NULL REFERENCES sys_tenant (id),
    code                   VARCHAR(64)  NOT NULL,
    name                   VARCHAR(200) NOT NULL,
    spec                   VARCHAR(255),
    category_id            BIGINT       REFERENCES md_item_category (id),
    item_type              VARCHAR(16)  NOT NULL,

    -- stock keeping unit and how purchasing orders it
    uom_code               VARCHAR(16)  NOT NULL REFERENCES md_uom (code),
    purchase_uom_code      VARCHAR(16)  REFERENCES md_uom (code),
    purchase_factor        NUMERIC(18, 6) NOT NULL DEFAULT 1,

    -- purchasing facet
    purchased              BOOLEAN      NOT NULL DEFAULT FALSE,
    purchase_lead_time_days INTEGER     NOT NULL DEFAULT 0,
    min_order_qty          NUMERIC(18, 6) NOT NULL DEFAULT 0,
    safety_stock           NUMERIC(18, 6) NOT NULL DEFAULT 0,
    default_supplier_id    BIGINT       REFERENCES md_partner (id),

    -- sales facet
    sold                   BOOLEAN      NOT NULL DEFAULT FALSE,
    sale_price             NUMERIC(18, 4),

    -- manufacturing facet
    manufactured           BOOLEAN      NOT NULL DEFAULT FALSE,
    lot_controlled         BOOLEAN      NOT NULL DEFAULT FALSE,
    serial_controlled      BOOLEAN      NOT NULL DEFAULT FALSE,
    shelf_life_days        INTEGER,

    -- costing facet; standard_cost is authoritative until M2 introduces moving average
    standard_cost          NUMERIC(18, 6) NOT NULL DEFAULT 0,
    currency               VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    tax_rate               NUMERIC(7, 4) NOT NULL DEFAULT 13,

    remark                 VARCHAR(500),
    status                 VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    row_version            BIGINT      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_item_code UNIQUE (tenant_id, code)
);
CREATE INDEX ix_md_item_tenant ON md_item (tenant_id);
CREATE INDEX ix_md_item_category ON md_item (category_id);
CREATE INDEX ix_md_item_name ON md_item (tenant_id, name);

CREATE TABLE md_work_center (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL REFERENCES sys_tenant (id),
    code                  VARCHAR(32)  NOT NULL,
    name                  VARCHAR(128) NOT NULL,
    department            VARCHAR(64),
    capacity_minutes_per_day INTEGER   NOT NULL DEFAULT 480,
    efficiency_percent    NUMERIC(7, 4) NOT NULL DEFAULT 100,
    cost_rate_per_hour    NUMERIC(18, 6) NOT NULL DEFAULT 0,
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    row_version           BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_work_center_code UNIQUE (tenant_id, code)
);
CREATE INDEX ix_md_work_center_tenant ON md_work_center (tenant_id);

-- One row per (item, version). Only RELEASED versions take part in MRP expansion;
-- the service layer keeps at most one RELEASED version per item.
CREATE TABLE md_bom (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES sys_tenant (id),
    item_id        BIGINT       NOT NULL REFERENCES md_item (id),
    version        VARCHAR(16)  NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    base_qty       NUMERIC(18, 6) NOT NULL DEFAULT 1,
    effective_from DATE,
    effective_to   DATE,
    remark         VARCHAR(500),
    released_at    TIMESTAMPTZ,
    row_version    BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_bom_item_version UNIQUE (tenant_id, item_id, version)
);
CREATE INDEX ix_md_bom_item ON md_bom (tenant_id, item_id);
CREATE INDEX ix_md_bom_status ON md_bom (tenant_id, status);

CREATE TABLE md_bom_line (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL REFERENCES sys_tenant (id),
    bom_id             BIGINT       NOT NULL REFERENCES md_bom (id) ON DELETE CASCADE,
    line_no            INTEGER      NOT NULL,
    component_item_id  BIGINT       NOT NULL REFERENCES md_item (id),
    qty                NUMERIC(18, 6) NOT NULL,
    loss_rate          NUMERIC(7, 4) NOT NULL DEFAULT 0,
    phantom            BOOLEAN      NOT NULL DEFAULT FALSE,
    remark             VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_bom_line_no UNIQUE (tenant_id, bom_id, line_no)
);
CREATE INDEX ix_md_bom_line_bom ON md_bom_line (bom_id);
CREATE INDEX ix_md_bom_line_component ON md_bom_line (tenant_id, component_item_id);

CREATE TABLE md_bom_substitute (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL REFERENCES sys_tenant (id),
    line_id            BIGINT       NOT NULL REFERENCES md_bom_line (id) ON DELETE CASCADE,
    substitute_item_id BIGINT       NOT NULL REFERENCES md_item (id),
    priority           INTEGER      NOT NULL DEFAULT 1,
    remark             VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_bom_substitute UNIQUE (tenant_id, line_id, substitute_item_id)
);
CREATE INDEX ix_md_bom_substitute_line ON md_bom_substitute (line_id);

CREATE TABLE md_routing (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES sys_tenant (id),
    item_id     BIGINT       NOT NULL REFERENCES md_item (id),
    version     VARCHAR(16)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    remark      VARCHAR(500),
    released_at TIMESTAMPTZ,
    row_version  BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_routing_item_version UNIQUE (tenant_id, item_id, version)
);
CREATE INDEX ix_md_routing_item ON md_routing (tenant_id, item_id);

CREATE TABLE md_routing_op (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL REFERENCES sys_tenant (id),
    routing_id        BIGINT       NOT NULL REFERENCES md_routing (id) ON DELETE CASCADE,
    seq               INTEGER      NOT NULL,
    work_center_id    BIGINT       NOT NULL REFERENCES md_work_center (id),
    name              VARCHAR(128) NOT NULL,
    setup_minutes     NUMERIC(12, 4) NOT NULL DEFAULT 0,
    run_minutes_per_unit NUMERIC(12, 4) NOT NULL DEFAULT 0,
    remark            VARCHAR(255),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_md_routing_op_seq UNIQUE (tenant_id, routing_id, seq)
);
CREATE INDEX ix_md_routing_op_routing ON md_routing_op (routing_id);
