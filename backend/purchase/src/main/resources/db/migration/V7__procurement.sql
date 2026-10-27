-- ============================================================================
-- V7 — procurement
--
-- Owned by the purchase module. One document table carries the three documents
-- that make up the buy side, because they are the same envelope: a supplier,
-- some lines with a price, and a life cycle. What differs is what a line means
-- and what may happen next:
--
--   REQUISITION  the floor asks for material. No supplier, no price yet.
--   ORDER        a promise to buy. Supplier and price are binding.
--   RECEIPT      goods actually arrived at the door. Posting one moves stock
--                and creates the payable.
--
-- The chain from one to the next is recorded rather than inferred: a purchase
-- order line carries the requisition line it came from, and a receipt line the
-- order line it satisfies. Without that, "why did we buy 200 of these?" has no
-- answer in the data.
--
-- Money is stored three ways on every line and every header: net, tax and
-- gross. unit_price is the price excluding tax, because that is what the
-- accounting needs; the gross is what the supplier actually invoices, and it is
-- derived rather than typed so the two can never disagree.
-- ============================================================================

CREATE TABLE pur_document (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL REFERENCES sys_tenant (id),
    doc_no            VARCHAR(32)   NOT NULL,
    doc_type          VARCHAR(16)   NOT NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    partner_id        BIGINT        REFERENCES md_partner (id),
    warehouse_id      BIGINT        REFERENCES inv_warehouse (id),
    location_id       BIGINT        REFERENCES inv_location (id),
    biz_date          DATE          NOT NULL,
    required_date     DATE,
    payment_term_days INTEGER       NOT NULL DEFAULT 0,
    net_amount        NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_amount        NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_amount      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    -- What this document was created from: a purchase order made from a
    -- requisition records the requisition number here.
    source_type       VARCHAR(32),
    source_id         VARCHAR(64),
    submitted_by      VARCHAR(64),
    submitted_at      TIMESTAMPTZ,
    approved_by       VARCHAR(64),
    approved_at       TIMESTAMPTZ,
    approval_remark   VARCHAR(255),
    posted_at         TIMESTAMPTZ,
    reversal_of       BIGINT        REFERENCES pur_document (id),
    remark            VARCHAR(500),
    row_version       BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_pur_document_no UNIQUE (tenant_id, doc_no)
);
CREATE INDEX ix_pur_document_tenant ON pur_document (tenant_id, doc_type, status);
CREATE INDEX ix_pur_document_partner ON pur_document (tenant_id, partner_id);
CREATE INDEX ix_pur_document_source ON pur_document (tenant_id, source_type, source_id);

COMMENT ON COLUMN pur_document.payment_term_days IS
    'How many days after the business date the payable falls due. Zero means due on receipt.';

CREATE TABLE pur_document_line (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT         NOT NULL REFERENCES sys_tenant (id),
    document_id    BIGINT         NOT NULL REFERENCES pur_document (id) ON DELETE CASCADE,
    line_no        INTEGER        NOT NULL,
    item_id        BIGINT         NOT NULL REFERENCES md_item (id),
    qty            NUMERIC(18, 6) NOT NULL,
    unit_price     NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_rate       NUMERIC(9, 6)  NOT NULL DEFAULT 0,
    net_amount     NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_amount     NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_amount   NUMERIC(18, 4) NOT NULL DEFAULT 0,
    -- How much of this line the next document in the chain has taken: ordered, for a
    -- requisition line; received, for an order line. One column serves both because it
    -- is the same question -- "how much of me is still open" -- asked one step along.
    qty_fulfilled  NUMERIC(18, 6) NOT NULL DEFAULT 0,
    source_line_id BIGINT         REFERENCES pur_document_line (id),
    warehouse_id   BIGINT         REFERENCES inv_warehouse (id),
    location_id    BIGINT         REFERENCES inv_location (id),
    lot_id         BIGINT         REFERENCES inv_lot (id),
    remark         VARCHAR(255),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_pur_document_line_no UNIQUE (document_id, line_no)
);
CREATE INDEX ix_pur_document_line_document ON pur_document_line (document_id);
CREATE INDEX ix_pur_document_line_source ON pur_document_line (tenant_id, source_line_id);

COMMENT ON COLUMN pur_document_line.unit_price IS
    'Price excluding tax. The gross price is unit_price * (1 + tax_rate).';
COMMENT ON COLUMN pur_document_line.tax_rate IS
    'VAT rate as a fraction, so 0.13 means thirteen percent.';
