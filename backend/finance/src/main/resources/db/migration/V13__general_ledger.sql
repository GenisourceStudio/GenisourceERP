-- ============================================================================
-- V13 — the general ledger
--
-- Three tables: a chart of accounts, vouchers, and the lines inside them.
-- Everything the finance module will ever report on is a query over these.
--
-- A voucher is the posting unit, not a document. Business documents already
-- exist in their own modules and already carry the amounts; a voucher is the
-- accounting sentence written about them ("goods arrived, we owe the supplier").
-- That is why a voucher remembers the document that caused it but never
-- duplicates it.
--
-- The unique index on (tenant_id, source_type, source_id) is the same guard the
-- receivables table uses: a retried post, an impatient second click or an
-- at-least-once message from MES must not book the money twice. source_id is
-- nullable for hand-written vouchers, and PostgreSQL treats NULLs as distinct,
-- so the index folds them with COALESCE(..., 0) — zero is not a value
-- BIGSERIAL can ever hand out.
--
-- Lines carry both a debit and a credit column rather than a signed amount.
-- A signed amount forces every reader to remember which side is positive for
-- which account type, and the first reader who guesses wrong produces a report
-- that is off by twice a number nobody can find.
-- ============================================================================

CREATE TABLE fin_account (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL REFERENCES sys_tenant (id),
    code              VARCHAR(32)  NOT NULL,
    name              VARCHAR(128) NOT NULL,
    -- ASSET / LIABILITY / EQUITY / REVENUE / EXPENSE / COST
    account_type      VARCHAR(16)  NOT NULL,
    -- DEBIT / CREDIT: which side increases this account
    balance_direction VARCHAR(8)   NOT NULL,
    parent_id         BIGINT       REFERENCES fin_account (id),
    level             INT          NOT NULL DEFAULT 1,
    -- only leaves may be posted to; a parent is a heading, not a bucket
    is_leaf           BOOLEAN      NOT NULL DEFAULT TRUE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Account determination. Automatic postings do not bind to a code, because a
    -- factory may renumber its chart ("we use 140301 for steel, not 1403") and a
    -- posting rule that hard-codes 1403 would then either fail or, worse, post to
    -- the wrong place silently. Rules ask for the INVENTORY_RAW role instead, and
    -- whoever owns accounting can point that role at the account they actually use.
    system_role       VARCHAR(32),
    remark            VARCHAR(255),
    row_version       BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_fin_account_code UNIQUE (tenant_id, code)
);
CREATE INDEX ix_fin_account_tree ON fin_account (tenant_id, parent_id, code);
CREATE INDEX ix_fin_account_type ON fin_account (tenant_id, account_type, active);

-- One account per role per tenant: two accounts answering to AP_ACCOUNT would
-- make every automatic posting a coin flip.
CREATE UNIQUE INDEX uq_fin_account_role
    ON fin_account (tenant_id, system_role)
    WHERE system_role IS NOT NULL;

CREATE TABLE fin_voucher (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL REFERENCES sys_tenant (id),
    voucher_no    VARCHAR(32)   NOT NULL,
    -- MANUAL / OPENING / PURCHASE / SALES / RECEIPT / PAYMENT / PRODUCTION
    voucher_type  VARCHAR(16)   NOT NULL,
    biz_date      DATE          NOT NULL,
    -- DRAFT -> POSTED -> REVERSED
    status        VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    source_type   VARCHAR(32),
    source_id     BIGINT,
    -- a reversing voucher points at the one it cancels; the original stays
    reversal_of   BIGINT        REFERENCES fin_voucher (id),
    total_debit   NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_credit  NUMERIC(18, 4) NOT NULL DEFAULT 0,
    posted_at     TIMESTAMPTZ,
    posted_by     VARCHAR(64),
    remark        VARCHAR(500),
    row_version   BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_fin_voucher_no UNIQUE (tenant_id, voucher_no)
);

-- One voucher per source document. Folding NULL with 0 keeps hand-written
-- vouchers out of the constraint entirely, which is what we want: they have
-- no source to be unique about.
CREATE UNIQUE INDEX uq_fin_voucher_source
    ON fin_voucher (tenant_id, source_type, COALESCE(source_id, 0));

CREATE INDEX ix_fin_voucher_date ON fin_voucher (tenant_id, biz_date DESC);
CREATE INDEX ix_fin_voucher_status ON fin_voucher (tenant_id, status, biz_date);
CREATE INDEX ix_fin_voucher_source_lookup ON fin_voucher (tenant_id, source_type, source_id);

CREATE TABLE fin_voucher_line (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL REFERENCES sys_tenant (id),
    voucher_id  BIGINT        NOT NULL REFERENCES fin_voucher (id) ON DELETE CASCADE,
    line_no     INT           NOT NULL,
    account_id  BIGINT        NOT NULL REFERENCES fin_account (id),
    debit       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    credit      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    -- sub-ledger dimensions: the same account, split by who and what
    partner_id  BIGINT        REFERENCES md_partner (id),
    summary     VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_fin_voucher_line_no UNIQUE (voucher_id, line_no),
    -- a line is on exactly one side; both empty or both filled is a bug
    CONSTRAINT ck_fin_voucher_line_side CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    ),
    CONSTRAINT ck_fin_voucher_line_non_negative CHECK (debit >= 0 AND credit >= 0)
);
CREATE INDEX ix_fin_voucher_line_voucher ON fin_voucher_line (voucher_id, line_no);
CREATE INDEX ix_fin_voucher_line_account ON fin_voucher_line (tenant_id, account_id);
CREATE INDEX ix_fin_voucher_line_partner ON fin_voucher_line (tenant_id, partner_id);

-- Money moving is an event, and the ledger row only keeps the running total
-- (amount_paid). A settlement row gives each payment or collection its own id,
-- which is what the payment voucher anchors to: settle the same debt three times
-- and there are three settlement rows, three vouchers, and one total that still
-- says exactly what is owed.
CREATE TABLE fin_settlement (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT        NOT NULL REFERENCES sys_tenant (id),
    side       VARCHAR(16)   NOT NULL,
    ledger_id  BIGINT        NOT NULL,
    amount     NUMERIC(18, 4) NOT NULL,
    biz_date   DATE          NOT NULL,
    remark     VARCHAR(255),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_fin_settlement_ledger ON fin_settlement (tenant_id, side, ledger_id);
