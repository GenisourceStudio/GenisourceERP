-- ============================================================================
-- V8 — sales
--
-- Owned by the sales module. Same shape as procurement for the same reason:
-- a quotation, an order and a shipment are one envelope with three lives.
--
--   QUOTATION  a price offered. Nothing is committed.
--   ORDER      a promise to deliver. Confirming one reserves stock.
--   SHIPMENT   goods actually left. Posting one releases the reservation,
--              issues the stock and creates the receivable.
--
-- The third table is the one procurement does not have. A reservation is a real
-- thing and not just a number on the balance: "these fifty are spoken for by
-- that order". Without recording which balance rows were promised to whom,
-- cancelling one order could not know what to give back, and a stock enquiry
-- could not say why a hundred on the shelf can only sell fifty.
-- ============================================================================

CREATE TABLE sal_document (
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
    source_type       VARCHAR(32),
    source_id         VARCHAR(64),
    submitted_by      VARCHAR(64),
    submitted_at      TIMESTAMPTZ,
    approved_by       VARCHAR(64),
    approved_at       TIMESTAMPTZ,
    approval_remark   VARCHAR(255),
    posted_at         TIMESTAMPTZ,
    reversal_of       BIGINT        REFERENCES sal_document (id),
    remark            VARCHAR(500),
    row_version       BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sal_document_no UNIQUE (tenant_id, doc_no)
);
CREATE INDEX ix_sal_document_tenant ON sal_document (tenant_id, doc_type, status);
CREATE INDEX ix_sal_document_partner ON sal_document (tenant_id, partner_id);
CREATE INDEX ix_sal_document_source ON sal_document (tenant_id, source_type, source_id);

CREATE TABLE sal_document_line (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT         NOT NULL REFERENCES sys_tenant (id),
    document_id    BIGINT         NOT NULL REFERENCES sal_document (id) ON DELETE CASCADE,
    line_no        INTEGER        NOT NULL,
    item_id        BIGINT         NOT NULL REFERENCES md_item (id),
    qty            NUMERIC(18, 6) NOT NULL,
    unit_price     NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_rate       NUMERIC(9, 6)  NOT NULL DEFAULT 0,
    net_amount     NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_amount     NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_amount   NUMERIC(18, 4) NOT NULL DEFAULT 0,
    -- How much of this line the next document in the chain has taken: ordered, for a
    -- quotation line; shipped, for an order line.
    qty_fulfilled  NUMERIC(18, 6) NOT NULL DEFAULT 0,
    source_line_id BIGINT         REFERENCES sal_document_line (id),
    warehouse_id   BIGINT         REFERENCES inv_warehouse (id),
    location_id    BIGINT         REFERENCES inv_location (id),
    lot_id         BIGINT         REFERENCES inv_lot (id),
    remark         VARCHAR(255),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_sal_document_line_no UNIQUE (document_id, line_no)
);
CREATE INDEX ix_sal_document_line_document ON sal_document_line (document_id);
CREATE INDEX ix_sal_document_line_source ON sal_document_line (tenant_id, source_line_id);

-- One row per balance line promised to one order line. A single order line can
-- therefore be spread over several batches or bins, which is the normal case:
-- fifty promised from the bin that has eighty, not from the one that has ten.
CREATE TABLE sal_reservation (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT         NOT NULL REFERENCES sys_tenant (id),
    order_id      BIGINT         NOT NULL REFERENCES sal_document (id) ON DELETE CASCADE,
    order_line_id BIGINT         NOT NULL REFERENCES sal_document_line (id) ON DELETE CASCADE,
    warehouse_id  BIGINT         NOT NULL REFERENCES inv_warehouse (id),
    location_id   BIGINT         REFERENCES inv_location (id),
    item_id       BIGINT         NOT NULL REFERENCES md_item (id),
    lot_id        BIGINT         REFERENCES inv_lot (id),
    qty           NUMERIC(18, 6) NOT NULL,
    qty_issued    NUMERIC(18, 6) NOT NULL DEFAULT 0,
    status        VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_sal_reservation_order ON sal_reservation (order_id, status);
CREATE INDEX ix_sal_reservation_line ON sal_reservation (order_line_id);
CREATE INDEX ix_sal_reservation_item ON sal_reservation (tenant_id, item_id, status);

COMMENT ON COLUMN sal_reservation.status IS
    'OPEN while the goods are promised, FULFILLED once shipped against them, RELEASED when the order gave them back.';
COMMENT ON COLUMN sal_reservation.qty_issued IS
    'How much of the reservation a shipment has already taken, so a partly shipped order keeps the rest reserved.';
