-- ============================================================================
-- V4 — inventory
--
-- Owned by the inventory module. Reading order:
--   inv_warehouse            where material is stored
--   inv_location             a bin inside a warehouse (optional)
--   inv_lot                  a batch or a serial number
--   inv_stock                the live balance, one row per key
--   inv_document / _line     the documents people post
--   inv_movement             the inventory ledger, one row per posting
--
-- Two ideas drive the shape of this schema:
--
-- 1. The balance and the ledger are separate. inv_stock answers "how much is
--    there now" and is rewritten in place; inv_movement answers "how did it
--    get that way" and is append-only. A stock query that had to replay the
--    ledger would be slow and a ledger that had to be rewritten would lose
--    its history. Both are updated in the same transaction, so they can never
--    disagree.
--
-- 2. A posting is never edited. A posted document is frozen and can only be
--    undone by a red-ink reversal that replays the original movements
--    backwards. This is the same rule as Chinese accounting: the original
--    voucher stays on the books, and the correction sits next to it.
-- ============================================================================

CREATE TABLE inv_warehouse (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES sys_tenant (id),
    code           VARCHAR(32)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    warehouse_type VARCHAR(16)  NOT NULL DEFAULT 'GENERAL',
    address        VARCHAR(255),
    remark         VARCHAR(500),
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    row_version    BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_inv_warehouse_code UNIQUE (tenant_id, code)
);
CREATE INDEX ix_inv_warehouse_tenant ON inv_warehouse (tenant_id);

CREATE TABLE inv_location (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES sys_tenant (id),
    warehouse_id BIGINT       NOT NULL REFERENCES inv_warehouse (id),
    code         VARCHAR(32)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    remark       VARCHAR(500),
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    row_version  BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_inv_location_code UNIQUE (tenant_id, warehouse_id, code)
);
CREATE INDEX ix_inv_location_warehouse ON inv_location (tenant_id, warehouse_id);

-- A batch and a serial number are the same row with a different type. A serial
-- is simply a lot with exactly one unit in it, which keeps the stock key and
-- the movement trail identical for both cases: one place to look, one rule.
CREATE TABLE inv_lot (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL REFERENCES sys_tenant (id),
    item_id         BIGINT       NOT NULL REFERENCES md_item (id),
    lot_no          VARCHAR(64)  NOT NULL,
    lot_type        VARCHAR(16)  NOT NULL DEFAULT 'LOT',
    production_date DATE,
    expiry_date     DATE,
    remark          VARCHAR(500),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    row_version     BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_inv_lot_item_no UNIQUE (tenant_id, item_id, lot_no)
);
CREATE INDEX ix_inv_lot_item ON inv_lot (tenant_id, item_id);
CREATE INDEX ix_inv_lot_expiry ON inv_lot (tenant_id, expiry_date);

-- The live balance. Four quantities, because "how much" is always ambiguous:
-- on_hand is physically there, reserved is promised to a sales order, locked
-- is held by quality or production, in_transit is on the road. Available is
-- on_hand - reserved - locked.
--
-- The unique key spans location_id and lot_id, both nullable: a material may
-- sit at warehouse level with no bin and no batch. PostgreSQL treats NULLs as
-- distinct in a unique index, so the index folds them with COALESCE — the
-- sentinel 0 can never collide because BIGSERIAL starts at 1.
CREATE TABLE inv_stock (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES sys_tenant (id),
    warehouse_id  BIGINT       NOT NULL REFERENCES inv_warehouse (id),
    location_id   BIGINT       REFERENCES inv_location (id),
    item_id       BIGINT       NOT NULL REFERENCES md_item (id),
    lot_id        BIGINT       REFERENCES inv_lot (id),
    on_hand       NUMERIC(18, 6) NOT NULL DEFAULT 0,
    reserved      NUMERIC(18, 6) NOT NULL DEFAULT 0,
    in_transit    NUMERIC(18, 6) NOT NULL DEFAULT 0,
    locked        NUMERIC(18, 6) NOT NULL DEFAULT 0,
    avg_cost      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_amount  NUMERIC(18, 4) NOT NULL DEFAULT 0,
    row_version   BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_inv_stock_key
    ON inv_stock (tenant_id, warehouse_id, COALESCE(location_id, 0), item_id, COALESCE(lot_id, 0));
CREATE INDEX ix_inv_stock_item ON inv_stock (tenant_id, item_id);
CREATE INDEX ix_inv_stock_lot ON inv_stock (tenant_id, lot_id);
CREATE INDEX ix_inv_stock_warehouse ON inv_stock (tenant_id, warehouse_id);

CREATE TABLE inv_document (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL REFERENCES sys_tenant (id),
    doc_no            VARCHAR(32)  NOT NULL,
    doc_type          VARCHAR(16)  NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    warehouse_id      BIGINT       NOT NULL REFERENCES inv_warehouse (id),
    location_id       BIGINT       REFERENCES inv_location (id),
    to_warehouse_id   BIGINT       REFERENCES inv_warehouse (id),
    to_location_id    BIGINT       REFERENCES inv_location (id),
    partner_id        BIGINT       REFERENCES md_partner (id),
    biz_date          DATE         NOT NULL,
    remark            VARCHAR(500),
    reversal_of       BIGINT       REFERENCES inv_document (id),
    posted_at         TIMESTAMPTZ,
    created_by        VARCHAR(64),
    row_version       BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_inv_document_no UNIQUE (tenant_id, doc_no)
);
CREATE INDEX ix_inv_document_type ON inv_document (tenant_id, doc_type);
CREATE INDEX ix_inv_document_status ON inv_document (tenant_id, status);
CREATE INDEX ix_inv_document_warehouse ON inv_document (tenant_id, warehouse_id);

CREATE TABLE inv_document_line (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL REFERENCES sys_tenant (id),
    document_id  BIGINT        NOT NULL REFERENCES inv_document (id) ON DELETE CASCADE,
    line_no      INTEGER       NOT NULL,
    item_id      BIGINT        NOT NULL REFERENCES md_item (id),
    lot_id       BIGINT        REFERENCES inv_lot (id),
    qty          NUMERIC(18, 6) NOT NULL,
    unit_cost    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    counted_qty  NUMERIC(18, 6),
    remark       VARCHAR(255),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_inv_document_line_no UNIQUE (tenant_id, document_id, line_no)
);
CREATE INDEX ix_inv_document_line_doc ON inv_document_line (document_id);
CREATE INDEX ix_inv_document_line_item ON inv_document_line (tenant_id, item_id);

-- The inventory ledger. Append-only: rows are written when a document is posted
-- and never touched again. id is a BIGSERIAL and therefore doubles as the
-- insertion order, which is the order the running balance has to follow.
-- balance_qty_after / balance_amount_after are stamped at write time so a
-- reader can follow the balance without replaying the whole ledger.
CREATE TABLE inv_movement (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT        NOT NULL REFERENCES sys_tenant (id),
    document_id          BIGINT        REFERENCES inv_document (id),
    document_line_id     BIGINT        REFERENCES inv_document_line (id),
    movement_type        VARCHAR(16)   NOT NULL,
    direction            VARCHAR(8)    NOT NULL,
    item_id              BIGINT        NOT NULL REFERENCES md_item (id),
    lot_id               BIGINT        REFERENCES inv_lot (id),
    warehouse_id         BIGINT        NOT NULL REFERENCES inv_warehouse (id),
    location_id          BIGINT        REFERENCES inv_location (id),
    qty                  NUMERIC(18, 6) NOT NULL,
    unit_cost            NUMERIC(18, 4) NOT NULL DEFAULT 0,
    amount               NUMERIC(18, 4) NOT NULL DEFAULT 0,
    balance_qty_after    NUMERIC(18, 6) NOT NULL DEFAULT 0,
    balance_amount_after NUMERIC(18, 4) NOT NULL DEFAULT 0,
    ref_type             VARCHAR(32),
    ref_id               VARCHAR(64),
    operator             VARCHAR(64),
    remark               VARCHAR(255),
    moved_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_inv_movement_item ON inv_movement (tenant_id, item_id, id);
CREATE INDEX ix_inv_movement_lot ON inv_movement (tenant_id, lot_id, id);
CREATE INDEX ix_inv_movement_document ON inv_movement (document_id);
CREATE INDEX ix_inv_movement_moved_at ON inv_movement (tenant_id, moved_at);

-- Document numbering. A factory talks about "the third receipt of the day", so the
-- number has to restart every day and per document type: RK-20261027-0001.
--
-- The counter is allocated with an atomic upsert, not with a count of existing rows.
-- A count would hand out the same number twice when two clerks save at the same
-- moment, and the unique index on doc_no would then abort one of them for no good
-- reason. The upsert locks the counter row for the rest of the statement, so the
-- numbers come out in a gapless sequence.
--
-- The table carries tenant_id but is not a Hibernate tenant-scoped entity: the
-- allocation runs as a native statement, so the tenant is passed explicitly.
CREATE TABLE inv_doc_counter (
    id        BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT      NOT NULL,
    prefix    VARCHAR(8)  NOT NULL,
    biz_date  DATE        NOT NULL,
    next_no   INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_inv_doc_counter UNIQUE (tenant_id, prefix, biz_date)
);
