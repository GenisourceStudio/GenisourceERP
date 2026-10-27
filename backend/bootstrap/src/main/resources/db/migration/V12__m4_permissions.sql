-- ============================================================================
-- V12 — the M4 permission catalogue
--
-- Owned by the bootstrap for the same reason V6 is: permissions are system-wide,
-- and V2's CROSS JOIN grant only covered the codes that existed when it ran.
--
-- The split follows the two new modules rather than the screens that use them.
-- Running MRP and executing a production order are different jobs done by
-- different people — a planner regenerates the plan every morning, a shop-floor
-- supervisor issues material and reports completions — so they get separate
-- codes and can be handed to different roles.
-- ============================================================================

INSERT INTO sys_permission (code, name, module, type, description) VALUES
    ('mrp:run:read',               '查看 MRP 运算', 'mrp',        'API', NULL),
    ('mrp:run:write',              '执行 MRP 运算', 'mrp',        'API', NULL),
    ('production:order:read',      '查看生产订单',  'production', 'API', NULL),
    ('production:order:write',     '维护生产订单',  'production', 'API', NULL),
    ('production:issue:write',     '生产领料',      'production', 'API', NULL),
    ('production:complete:write',  '完工入库',      'production', 'API', NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'mrp:run:read', 'mrp:run:write',
    'production:order:read', 'production:order:write',
    'production:issue:write', 'production:complete:write'
)
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
