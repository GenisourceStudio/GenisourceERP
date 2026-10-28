-- ============================================================================
-- V14 — the M5 permission catalogue
--
-- V6 already defined finance:voucher:read and finance:voucher:write when the
-- only thing behind them was the receivables and payables ledger. M5 keeps
-- those two names and adds what the general ledger actually needs:
--
--   * accounts are their own job. Changing the chart of accounts changes every
--     report ever printed from it, so it is not something a clerk who types
--     vouchers should be able to do by accident.
--   * posting is separated from writing. A voucher is typed by one person and
--     posted by another; that separation is the whole point of the review step
--     in Chinese accounting practice, and a permission model that cannot
--     express it forces every factory to invent a paper process around it.
--   * integration endpoints are machine endpoints. They get their own codes so
--     an MES service account can be granted exactly the two it needs and
--     nothing a human would use.
-- ============================================================================

INSERT INTO sys_permission (code, name, module, type, description) VALUES
    ('finance:account:read',     '查看会计科目', 'finance',     'API', NULL),
    ('finance:account:write',    '维护会计科目', 'finance',     'API', NULL),
    ('finance:voucher:post',     '凭证过账',     'finance',     'API', NULL),
    ('finance:report:read',      '查看财务报表', 'finance',     'API', NULL),
    ('integration:mes:read',     '查看 MES 集成', 'integration', 'API', NULL),
    ('integration:mes:write',    'MES 集成操作',  'integration', 'API', NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'finance:account:read', 'finance:account:write',
    'finance:voucher:post', 'finance:report:read',
    'integration:mes:read', 'integration:mes:write'
)
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
