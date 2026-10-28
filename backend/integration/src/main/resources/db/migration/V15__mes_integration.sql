-- ============================================================================
-- V15 — MES integration
--
-- Owned by the integration module. Two tables, and one column added to somebody
-- else's table.
--
-- The outbox is where a message to MES waits. It exists because a release and a
-- delivery to another system cannot be one atomic act: either the order is released
-- and the message is lost, or the message is sent and the release rolls back. So the
-- release writes the message here in the same transaction, and a worker delivers it
-- afterwards. A message that fails is retried with a growing gap between attempts;
-- one that keeps failing is marked dead rather than retried forever, because a
-- message nobody will ever deliver should stop asking.
--
-- The completions table records what MES has told us has been made. Its whole
-- purpose is the unique mes_completion_id: MES retries, networks retry, and a
-- completion that books stock twice is a factory that cannot be trusted to count.
--
-- The one column on prd_production_order is where MES says the order has got to.
-- It lives on the order because that is where a reader looks, and it is written only
-- by MES progress reports.
-- ============================================================================

CREATE TABLE int_mes_outbox (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL REFERENCES sys_tenant (id),
    -- WORK_ORDER / PROGRESS / ITEM
    message_type    VARCHAR(32)   NOT NULL,
    -- what the message is about, so a reader can tell what went where
    subject         VARCHAR(64)   NOT NULL,
    -- the idempotency key: one message per subject, however many times it is retried
    dedupe_key      VARCHAR(128)  NOT NULL,
    payload         TEXT          NOT NULL,
    -- PENDING / SENT / DEAD
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    attempts        INT           NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_error      VARCHAR(500),
    sent_at         TIMESTAMPTZ,
    row_version     BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_int_outbox_dedupe UNIQUE (tenant_id, dedupe_key)
);
CREATE INDEX ix_int_outbox_due ON int_mes_outbox (tenant_id, status, next_attempt_at);

CREATE TABLE int_mes_completion (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT        NOT NULL REFERENCES sys_tenant (id),
    mes_completion_id VARCHAR(64)  NOT NULL,
    mes_work_order_no VARCHAR(64) NOT NULL,
    production_order_id BIGINT     REFERENCES prd_production_order (id),
    completed_qty    NUMERIC(18, 6) NOT NULL DEFAULT 0,
    scrap_qty        NUMERIC(18, 6) NOT NULL DEFAULT 0,
    completed_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_int_completion_id UNIQUE (tenant_id, mes_completion_id)
);
CREATE INDEX ix_int_completion_order ON int_mes_completion (tenant_id, mes_work_order_no);

ALTER TABLE prd_production_order
    ADD COLUMN mes_progress_qty NUMERIC(18, 6) NOT NULL DEFAULT 0;
