CREATE TABLE access_roles (
    role_key VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    hierarchy_level INTEGER NOT NULL CHECK (hierarchy_level >= 0),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE access_permissions (
    permission_key VARCHAR(120) PRIMARY KEY,
    module_name VARCHAR(64) NOT NULL,
    description VARCHAR(240) NOT NULL
);

CREATE TABLE access_role_permissions (
    role_key VARCHAR(64) NOT NULL REFERENCES access_roles(role_key) ON DELETE CASCADE,
    permission_key VARCHAR(120) NOT NULL REFERENCES access_permissions(permission_key) ON DELETE CASCADE,
    PRIMARY KEY (role_key, permission_key)
);

CREATE TABLE access_audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT,
    actor_email VARCHAR(255),
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    before_value JSONB,
    after_value JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_access_audit_created ON access_audit_log(created_at DESC);

INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role) VALUES
    ('SUPER_ADMIN', 'Super Admin', 1000, TRUE),
    ('ADMIN', 'Administrator', 800, TRUE),
    ('MANAGER', 'Manager', 500, TRUE),
    ('ACCOUNTANT', 'Accountant', 350, TRUE),
    ('AUDITOR', 'Auditor', 250, TRUE),
    ('USER', 'Standard User', 100, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_permissions(permission_key, module_name, description) VALUES
    ('application:admin', 'application', 'Manage platform-wide configuration'),
    ('users:read', 'users', 'View user directory'),
    ('users:manage', 'users', 'Change user roles and account status'),
    ('roles:manage', 'roles', 'Create roles and grant permissions within delegated authority'),
    ('accounting:read', 'accounting', 'View accounting records and reports'),
    ('accounting:write', 'accounting', 'Create and update accounting records'),
    ('accounting:approve', 'accounting', 'Approve or reverse accounting entries'),
    ('accounting:reconcile', 'accounting', 'Update reconciliation decisions'),
    ('accounting:import', 'accounting', 'Import accounting data'),
    ('accounting:export', 'accounting', 'Export accounting data'),
    ('crm:read', 'crm', 'View CRM records'),
    ('crm:manage', 'crm', 'Manage CRM records'),
    ('inventory:read', 'inventory', 'View inventory records'),
    ('inventory:manage', 'inventory', 'Manage inventory records'),
    ('stock:read', 'stock', 'View stock records'),
    ('stock:manage', 'stock', 'Manage stock records'),
    ('analytics:read', 'analytics', 'View analytics'),
    ('loyalty:manage', 'loyalty', 'Manage loyalty operations'),
    ('orders:manage', 'orders', 'Manage orders'),
    ('procurement:read', 'procurement', 'View procurement records'),
    ('procurement:manage', 'procurement', 'Manage procurement records'),
    ('errors:manage', 'error-stack', 'View and manage error diagnostics'),
    ('catalog:manage', 'catalog', 'Manage product catalog and offers')
ON CONFLICT (permission_key) DO NOTHING;

-- Keep the existing administrator login as the initial root only when it is active.
UPDATE users
SET role = 'SUPER_ADMIN'
WHERE id = (
    SELECT id FROM users
    WHERE active = TRUE AND upper(role) IN ('ADMIN', 'ROLE_ADMIN')
      AND NOT EXISTS (SELECT 1 FROM users WHERE active = TRUE AND upper(role) = 'SUPER_ADMIN')
    ORDER BY id LIMIT 1
);

UPDATE users SET role = upper(regexp_replace(role, '^ROLE_', '')) WHERE role LIKE 'ROLE_%';
UPDATE users SET role = 'USER' WHERE NOT EXISTS (SELECT 1 FROM access_roles r WHERE r.role_key = users.role);

INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'SUPER_ADMIN', permission_key FROM access_permissions ON CONFLICT DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'ADMIN', permission_key FROM access_permissions WHERE permission_key <> 'application:admin' ON CONFLICT DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('MANAGER','users:read'),('MANAGER','users:manage'),('MANAGER','roles:manage'),
    ('MANAGER','accounting:read'),('MANAGER','accounting:write'),('MANAGER','accounting:approve'),('MANAGER','accounting:reconcile'),('MANAGER','accounting:import'),('MANAGER','accounting:export'),
    ('MANAGER','crm:read'),('MANAGER','crm:manage'),('MANAGER','inventory:read'),('MANAGER','inventory:manage'),
    ('MANAGER','stock:read'),('MANAGER','stock:manage'),('MANAGER','analytics:read'),('MANAGER','loyalty:manage'),('MANAGER','orders:manage'),
    ('MANAGER','procurement:read'),('MANAGER','procurement:manage'),('MANAGER','errors:manage'),
    ('ACCOUNTANT','accounting:read'),('ACCOUNTANT','accounting:write'),('ACCOUNTANT','accounting:reconcile'),('ACCOUNTANT','accounting:import'),('ACCOUNTANT','accounting:export'),
    ('AUDITOR','accounting:read'),('AUDITOR','accounting:export'),('AUDITOR','analytics:read')
ON CONFLICT DO NOTHING;

INSERT INTO access_audit_log(actor_user_id, actor_email, action, target_type, target_id, after_value)
SELECT id, email, 'RBAC_BOOTSTRAP', 'ROLE', 'SUPER_ADMIN', jsonb_build_object('role', 'SUPER_ADMIN')
FROM users WHERE role = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
