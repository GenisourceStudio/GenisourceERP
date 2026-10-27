-- ============================================================================
-- V2 — baseline seed
--
-- A private deployment boots straight into a usable state: one tenant, a permission
-- catalogue and an ADMIN role holding every permission. The administrator account
-- itself is created by the application (InitialAdminCreator) because its password hash
-- must come from the configured secret, not from a committed file.
-- ============================================================================

INSERT INTO sys_tenant (code, name, status)
VALUES ('default', '默认组织', 'ACTIVE');

-- Permission catalogue. Codes follow <module>:<resource>:<action>; the SPA derives
-- menu visibility from the MENU entries and button state from BUTTON entries.
INSERT INTO sys_permission (code, name, module, type, description) VALUES
    ('sys:user:read',      '查看用户',       'sys', 'API',    NULL),
    ('sys:user:write',     '维护用户',       'sys', 'API',    NULL),
    ('sys:role:read',      '查看角色',       'sys', 'API',    NULL),
    ('sys:role:write',     '维护角色',       'sys', 'API',    NULL),
    ('sys:audit:read',     '查看审计日志',   'sys', 'API',    NULL),
    ('sys:tenant:read',    '查看租户',       'sys', 'API',    NULL),
    ('sys:tenant:write',   '维护租户',       'sys', 'API',    NULL),

    ('masterdata:item:read',      '查看物料',     'masterdata', 'API', NULL),
    ('masterdata:item:write',     '维护物料',     'masterdata', 'API', NULL),
    ('masterdata:bom:read',       '查看 BOM',     'masterdata', 'API', NULL),
    ('masterdata:bom:write',      '维护 BOM',     'masterdata', 'API', NULL),
    ('masterdata:partner:read',   '查看客户/供应商', 'masterdata', 'API', NULL),
    ('masterdata:partner:write',  '维护客户/供应商', 'masterdata', 'API', NULL),
    ('masterdata:routing:read',   '查看工艺路线', 'masterdata', 'API', NULL),
    ('masterdata:routing:write',  '维护工艺路线', 'masterdata', 'API', NULL),

    ('inventory:stock:read',      '查看库存',     'inventory', 'API', NULL),
    ('inventory:stock:write',     '库存作业',     'inventory', 'API', NULL),

    ('purchase:order:read',       '查看采购',     'purchase', 'API', NULL),
    ('purchase:order:write',      '维护采购',     'purchase', 'API', NULL),

    ('sales:order:read',          '查看销售',     'sales', 'API', NULL),
    ('sales:order:write',         '维护销售',     'sales', 'API', NULL),

    ('mrp:run:read',              '查看 MRP',     'mrp', 'API', NULL),
    ('mrp:run:write',             '执行 MRP',     'mrp', 'API', NULL),

    ('production:order:read',     '查看生产订单', 'production', 'API', NULL),
    ('production:order:write',    '维护生产订单', 'production', 'API', NULL),

    ('finance:voucher:read',      '查看凭证',     'finance', 'API', NULL),
    ('finance:voucher:write',     '维护凭证',     'finance', 'API', NULL);

INSERT INTO sys_role (tenant_id, code, name, description, builtin)
SELECT id, 'ADMIN', '系统管理员', '拥有全部权限，内置角色不可删除', TRUE
FROM sys_tenant WHERE code = 'default';

INSERT INTO sys_role (tenant_id, code, name, description, builtin)
SELECT id, 'OPERATOR', '业务操作员', '日常单据录入权限，M1 起逐模块细化', TRUE
FROM sys_tenant WHERE code = 'default';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'masterdata:item:read', 'masterdata:bom:read', 'masterdata:partner:read',
    'inventory:stock:read', 'purchase:order:read', 'sales:order:read'
)
WHERE r.code = 'OPERATOR';
