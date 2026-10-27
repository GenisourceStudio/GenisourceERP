-- ============================================================================
-- V1 — platform baseline
--
-- Conventions used throughout the schema:
--   * every business table carries tenant_id; Hibernate injects it into all SQL
--   * timestamps are TIMESTAMPTZ, values are always UTC at rest
--   * status columns are VARCHAR with a CHECK-free enum in the application, so new
--     states do not require a migration
--   * money and quantities are NUMERIC, never floating point
-- ============================================================================

CREATE TABLE sys_tenant (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(64)  NOT NULL,
    name       VARCHAR(128) NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_sys_tenant_code UNIQUE (code)
);

CREATE TABLE sys_user (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL REFERENCES sys_tenant (id),
    username              VARCHAR(64)  NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    display_name          VARCHAR(128) NOT NULL,
    email                 VARCHAR(128),
    phone                 VARCHAR(32),
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    force_password_change BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at         TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_sys_user_username UNIQUE (tenant_id, username)
);
CREATE INDEX ix_sys_user_tenant ON sys_user (tenant_id);

CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES sys_tenant (id),
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    builtin     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_sys_role_code UNIQUE (tenant_id, code)
);
CREATE INDEX ix_sys_role_tenant ON sys_role (tenant_id);

-- The permission catalogue is global: a code such as masterdata:item:write means the
-- same thing in every factory, so it is not tenant scoped.
CREATE TABLE sys_permission (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(128) NOT NULL,
    name        VARCHAR(128) NOT NULL,
    module      VARCHAR(64)  NOT NULL,
    type        VARCHAR(16)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_sys_permission_code UNIQUE (code)
);
CREATE INDEX ix_sys_permission_module ON sys_permission (module);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES sys_permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- tenant_id is intentionally not a foreign key: audit rows must outlive a tenant
-- rename and must never be cascade-deleted.
CREATE TABLE sys_audit_log (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT      NOT NULL,
    user_id       BIGINT,
    username      VARCHAR(64),
    action        VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id   VARCHAR(64),
    detail        JSONB,
    ip_address    VARCHAR(64),
    user_agent    VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sys_audit_log_tenant_time ON sys_audit_log (tenant_id, created_at DESC);
CREATE INDEX ix_sys_audit_log_action ON sys_audit_log (action);
