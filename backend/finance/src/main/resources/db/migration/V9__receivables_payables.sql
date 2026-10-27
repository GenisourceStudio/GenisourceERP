-- ============================================================================
-- V9 — receivables and payables
--
-- Owned by the finance module. These two are not documents: nobody types them
-- in. They are the arithmetic consequence of a posted receipt or shipment, and
-- they exist so that "who owes us what, and when" is a query rather than a
-- recalculation over every document ever posted.
--
-- Each row remembers the document that produced it, and the unique constraint
-- on that pair is what makes the creation idempotent: posting the same receipt
-- twice cannot create two payables, which is the failure a retry or a double
-- click would otherwise produce.
--
-- M5 turns these into vouchers and posts them to the general ledger. Until then
-- they are a ledger of their own: amounts, what has been settled against them,
-- and when they fall due.
-- ============================================================================

CREATE TABLE fin_payable (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL REFERENCES sys_tenant (id),
    partner_id   BIGINT        NOT NULL REFERENCES md_partner (id),
    source_type  VARCHAR(32)   NOT NULL,
    source_id    BIGINT        NOT NULL,
    source_no    VARCHAR(32)   NOT NULL,
    biz_date     DATE          NOT NULL,
    due_date     DATE,
    net_amount   NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_amount   NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18, 4) NOT NULL DEFAULT 0,
    amount_paid  NUMERIC(18, 4) NOT NULL DEFAULT 0,
    status       VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    remark       VARCHAR(500),
    row_version  BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_fin_payable_source UNIQUE (tenant_id, source_type, source_id)
);
CREATE INDEX ix_fin_payable_partner ON fin_payable (tenant_id, partner_id, status);
CREATE INDEX ix_fin_payable_due ON fin_payable (tenant_id, status, due_date);

CREATE TABLE fin_receivable (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL REFERENCES sys_tenant (id),
    partner_id   BIGINT        NOT NULL REFERENCES md_partner (id),
    source_type  VARCHAR(32)   NOT NULL,
    source_id    BIGINT        NOT NULL,
    source_no    VARCHAR(32)   NOT NULL,
    biz_date     DATE          NOT NULL,
    due_date     DATE,
    net_amount   NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_amount   NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18, 4) NOT NULL DEFAULT 0,
    amount_paid  NUMERIC(18, 4) NOT NULL DEFAULT 0,
    status       VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    remark       VARCHAR(500),
    row_version  BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_fin_receivable_source UNIQUE (tenant_id, source_type, source_id)
);
CREATE INDEX ix_fin_receivable_partner ON fin_receivable (tenant_id, partner_id, status);
CREATE INDEX ix_fin_receivable_due ON fin_receivable (tenant_id, status, due_date);

COMMENT ON COLUMN fin_payable.amount_paid IS
    'Settled against this payable. The status is derived from it, not stored independently, so the two cannot drift apart.';
COMMENT ON COLUMN fin_receivable.amount_paid IS
    'Settled against this receivable. The status is derived from it, not stored independently, so the two cannot drift apart.';
