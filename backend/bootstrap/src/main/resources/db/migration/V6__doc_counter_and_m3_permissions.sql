-- ============================================================================
-- V6 — one document-number allocator, and the M3 permission catalogue
--
-- Owned by the bootstrap, because both halves are system-wide.
--
-- 1. sys_doc_counter. M2 gave the inventory module its own counter table. M3
--    adds purchase, sales and finance documents, and each of them needs the
--    same thing: "<prefix>-<date>-<daily sequence>", allocated atomically so
--    two clerks saving at the same moment never get the same number. Three
--    copies of that table and its upsert would be three places to get wrong,
--    so the counter moves here and the existing rows come with it.
--
-- 2. The M3 permissions. V2 grants ADMIN everything with a CROSS JOIN, which
--    only covers what existed when it ran, so new codes have to be granted
--    explicitly afterwards.
-- ============================================================================

CREATE TABLE sys_doc_counter (
    id        BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT      NOT NULL,
    prefix    VARCHAR(8)  NOT NULL,
    biz_date  DATE        NOT NULL,
    next_no   INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_sys_doc_counter UNIQUE (tenant_id, prefix, biz_date)
);

COMMENT ON TABLE sys_doc_counter IS
    'Allocates per-tenant, per-document-type, per-day sequence numbers. Readers must never count existing rows to derive the next number: two concurrent saves would hand out the same one.';

-- Carry the counters M2 already issued across, then drop the old table. Nothing
-- historical is lost: a counter holds only "what comes next", and the numbers it
-- already handed out live on the documents themselves.
INSERT INTO sys_doc_counter (tenant_id, prefix, biz_date, next_no)
SELECT tenant_id, prefix, biz_date, next_no
FROM inv_doc_counter;

DROP TABLE inv_doc_counter;

-- ---------------------------------------------------------------- permissions

INSERT INTO sys_permission (code, name, module, type, description) VALUES
    ('purchase:requisition:read',  '查看请购单',   'purchase', 'API', NULL),
    ('purchase:requisition:write', '维护请购单',   'purchase', 'API', NULL),
    ('purchase:receipt:read',      '查看收料单',   'purchase', 'API', NULL),
    ('purchase:receipt:write',     '维护收料单',   'purchase', 'API', NULL),
    ('sales:quotation:read',       '查看报价单',   'sales',    'API', NULL),
    ('sales:quotation:write',      '维护报价单',   'sales',    'API', NULL),
    ('sales:shipment:read',        '查看发货单',   'sales',    'API', NULL),
    ('sales:shipment:write',       '维护发货单',   'sales',    'API', NULL),
    ('finance:receivable:read',    '查看应收',     'finance',  'API', NULL),
    ('finance:receivable:write',   '维护应收',     'finance',  'API', NULL),
    ('finance:payable:read',       '查看应付',     'finance',  'API', NULL),
    ('finance:payable:write',      '维护应付',     'finance',  'API', NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'purchase:requisition:read', 'purchase:requisition:write',
    'purchase:receipt:read', 'purchase:receipt:write',
    'sales:quotation:read', 'sales:quotation:write',
    'sales:shipment:read', 'sales:shipment:write',
    'finance:receivable:read', 'finance:receivable:write',
    'finance:payable:read', 'finance:payable:write'
)
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
