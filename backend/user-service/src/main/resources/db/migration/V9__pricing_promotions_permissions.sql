-- Permission keys and system roles for the Pricing & Promotions module of the E-commerce Admin application.
-- Enforced in product-service (PricingAdminController) from the tenant-scoped JWT permission claim.
INSERT INTO access_permissions(permission_key, module_name, description) VALUES
    ('pricing:read', 'pricing', 'View prices, price history, scheduled prices and the pricing dashboard'),
    ('pricing:create', 'pricing', 'Create product prices and schedule price changes'),
    ('pricing:update', 'pricing', 'Update, bulk-update and cancel scheduled product prices'),
    ('pricing:approve', 'pricing', 'Approve or reject scheduled price changes'),
    ('pricing:publish', 'pricing', 'Publish campaigns, roll back prices, make large price changes and edit pricing policy'),
    ('pricing:cost:read', 'pricing', 'View and edit product cost prices'),
    ('pricing:export', 'pricing', 'Export price lists'),
    ('pricing:audit', 'pricing', 'View pricing and promotion audit history'),
    ('promotion:read', 'pricing', 'View promotion campaigns, calendar, approvals and performance'),
    ('promotion:create', 'pricing', 'Create promotion campaigns'),
    ('promotion:update', 'pricing', 'Edit, submit, pause and cancel promotion campaigns'),
    ('promotion:approve', 'pricing', 'Approve or reject promotion campaigns'),
    ('coupon:manage', 'pricing', 'Generate, pause and revoke coupons and view redemptions')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role) VALUES
    ('PRICING_MANAGER', 'Pricing Manager', 450, TRUE),
    ('PRICING_APPROVER', 'Pricing Approver', 550, TRUE),
    ('MARKETING_MANAGER', 'Marketing Manager', 400, TRUE),
    ('PRICING_ANALYST', 'Pricing Analyst', 150, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'ADMIN', permission_key FROM access_permissions WHERE module_name = 'pricing'
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('PRICING_MANAGER','pricing:read'), ('PRICING_MANAGER','pricing:create'), ('PRICING_MANAGER','pricing:update'),
    ('PRICING_MANAGER','pricing:cost:read'), ('PRICING_MANAGER','pricing:export'),
    ('PRICING_MANAGER','promotion:read'), ('PRICING_MANAGER','promotion:create'), ('PRICING_MANAGER','promotion:update'),
    ('PRICING_APPROVER','pricing:read'), ('PRICING_APPROVER','pricing:approve'), ('PRICING_APPROVER','pricing:publish'),
    ('PRICING_APPROVER','pricing:audit'), ('PRICING_APPROVER','promotion:read'), ('PRICING_APPROVER','promotion:approve'),
    ('MARKETING_MANAGER','pricing:read'), ('MARKETING_MANAGER','promotion:read'), ('MARKETING_MANAGER','promotion:create'),
    ('MARKETING_MANAGER','promotion:update'), ('MARKETING_MANAGER','coupon:manage'),
    ('PRICING_ANALYST','pricing:read'), ('PRICING_ANALYST','promotion:read'), ('PRICING_ANALYST','pricing:audit')
ON CONFLICT DO NOTHING;
