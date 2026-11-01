-- Tenant and application control plane. Keep legacy accounts and records intact.
CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY,
    slug VARCHAR(63) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

CREATE TABLE application_registry (
    application_code VARCHAR(48) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    approved_origins TEXT[] NOT NULL DEFAULT '{}',
    required_permissions TEXT[] NOT NULL DEFAULT '{}',
    supported_plan_codes TEXT[] NOT NULL DEFAULT '{}',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_application_registry_status CHECK (status IN ('ACTIVE', 'PLANNED', 'SUSPENDED'))
);

CREATE TABLE tenant_memberships (
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT ck_tenant_memberships_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INVITED'))
);

CREATE TABLE tenant_application_entitlements (
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    application_code VARCHAR(48) NOT NULL REFERENCES application_registry(application_code),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    plan_code VARCHAR(48),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, application_code)
);

CREATE TABLE user_application_grants (
    tenant_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    application_code VARCHAR(48) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    granted_by BIGINT REFERENCES users(id),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (tenant_id, user_id, application_code),
    FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_memberships(tenant_id, user_id),
    FOREIGN KEY (tenant_id, application_code) REFERENCES tenant_application_entitlements(tenant_id, application_code)
);

CREATE TABLE tenant_role_assignments (
    tenant_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    application_code VARCHAR(48) NOT NULL,
    role_key VARCHAR(64) NOT NULL REFERENCES access_roles(role_key),
    assigned_by BIGINT REFERENCES users(id),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, user_id, application_code),
    FOREIGN KEY (tenant_id, user_id, application_code)
        REFERENCES user_application_grants(tenant_id, user_id, application_code)
);

CREATE INDEX idx_tenant_memberships_user ON tenant_memberships(user_id, status);
CREATE INDEX idx_tenant_grants_user_app ON user_application_grants(user_id, application_code, active);
CREATE INDEX idx_tenant_entitlements_app ON tenant_application_entitlements(application_code, enabled);

CREATE TABLE tenant_access_audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(tenant_id),
    actor_user_id BIGINT REFERENCES users(id),
    actor_email VARCHAR(255),
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(160) NOT NULL,
    before_value JSONB,
    after_value JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_audit_time ON tenant_access_audit_log(tenant_id, created_at DESC);

INSERT INTO application_registry(application_code, display_name, status, required_permissions, supported_plan_codes) VALUES
    ('SUPER_ADMIN', 'Super Admin Portal', 'ACTIVE', ARRAY['application:admin'], ARRAY[]::text[]),
    ('ECOMMERCE_ADMIN', 'E-commerce Admin', 'PLANNED', ARRAY['catalog:manage'], ARRAY[]::text[]),
    ('STOREFRONT', 'Customer Storefront', 'PLANNED', ARRAY[]::text[], ARRAY[]::text[]),
    ('INVENTORY', 'Inventory Management', 'ACTIVE', ARRAY['inventory:read'], ARRAY[]::text[]),
    ('ACCOUNTING', 'Accounting and Finance', 'PLANNED', ARRAY['accounting:read'], ARRAY[]::text[]),
    ('DELIVERY_OPS', 'Delivery Operations', 'PLANNED', ARRAY[]::text[], ARRAY[]::text[]),
    ('VENDOR_PORTAL', 'Vendor and Restaurant Portal', 'PLANNED', ARRAY[]::text[], ARRAY[]::text[]),
    ('CRM', 'CRM', 'PLANNED', ARRAY['crm:read'], ARRAY[]::text[]),
    ('QUALITY_CENTER', 'Tester / Quality Center', 'ACTIVE', ARRAY['qa:read'], ARRAY[]::text[]),
    ('CUSTOMER_SUPPORT', 'Customer Support', 'PLANNED', ARRAY[]::text[], ARRAY[]::text[])
ON CONFLICT (application_code) DO NOTHING;

INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role)
VALUES ('WAREHOUSE_OPERATOR', 'Warehouse Operator', 220, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('WAREHOUSE_OPERATOR', 'inventory:read'),
    ('WAREHOUSE_OPERATOR', 'inventory:manage')
ON CONFLICT DO NOTHING;

-- A stable migration owner for existing records. New client data must use newly created tenant IDs.
INSERT INTO tenants(tenant_id, slug, display_name, status)
VALUES ('00000000-0000-4000-8000-000000000001', 'legacy', 'Legacy UCP Tenant', 'ACTIVE')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO tenant_memberships(tenant_id, user_id, status)
SELECT '00000000-0000-4000-8000-000000000001', id, CASE WHEN active THEN 'ACTIVE' ELSE 'SUSPENDED' END
FROM users
ON CONFLICT (tenant_id, user_id) DO NOTHING;
