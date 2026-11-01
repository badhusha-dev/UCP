INSERT INTO access_permissions(permission_key, module_name, description) VALUES
    ('delivery:read', 'delivery', 'View delivery operations within an assigned client'),
    ('delivery:manage', 'delivery', 'Create and manage deliveries within an assigned client'),
    ('delivery:dispatch', 'delivery', 'Dispatch and reassign eligible delivery partners'),
    ('delivery:partners:read', 'delivery', 'View eligible partner operational profiles'),
    ('delivery:partners:manage', 'delivery', 'Review and manage partner eligibility'),
    ('delivery:partner:self', 'delivery', 'Manage own availability and assigned delivery work'),
    ('delivery:tracking', 'delivery', 'View and submit delivery location updates'),
    ('delivery:proof', 'delivery', 'Capture and view delivery completion evidence'),
    ('delivery:incident', 'delivery', 'Report and manage delivery incidents'),
    ('delivery:config', 'delivery', 'Manage delivery zones and operating policy'),
    ('delivery:report', 'delivery', 'View delivery operations and earnings reports'),
    ('delivery:audit', 'delivery', 'View delivery audit history')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role) VALUES
    ('DELIVERY_ADMIN', 'Delivery Administrator', 700, TRUE),
    ('DISPATCHER', 'Delivery Dispatcher', 500, TRUE),
    ('DELIVERY_PARTNER', 'Delivery Partner', 200, TRUE),
    ('DELIVERY_ANALYST', 'Delivery Analyst', 150, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'DELIVERY_ADMIN', permission_key FROM access_permissions WHERE module_name='delivery'
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('DISPATCHER','delivery:read'), ('DISPATCHER','delivery:manage'), ('DISPATCHER','delivery:dispatch'),
    ('DISPATCHER','delivery:partners:read'), ('DISPATCHER','delivery:tracking'),
    ('DISPATCHER','delivery:incident'), ('DISPATCHER','delivery:report'),
    ('DELIVERY_PARTNER','delivery:read'), ('DELIVERY_PARTNER','delivery:partner:self'),
    ('DELIVERY_PARTNER','delivery:tracking'), ('DELIVERY_PARTNER','delivery:proof'), ('DELIVERY_PARTNER','delivery:incident'),
    ('DELIVERY_ANALYST','delivery:read'), ('DELIVERY_ANALYST','delivery:tracking'),
    ('DELIVERY_ANALYST','delivery:report'), ('DELIVERY_ANALYST','delivery:audit')
ON CONFLICT DO NOTHING;

UPDATE application_registry
SET status='ACTIVE',
    required_permissions=ARRAY['delivery:read'],
    metadata=metadata || '{"independentFrontend":true,"service":"delivery-service"}'::jsonb,
    updated_at=CURRENT_TIMESTAMP
WHERE application_code='DELIVERY_OPS';
