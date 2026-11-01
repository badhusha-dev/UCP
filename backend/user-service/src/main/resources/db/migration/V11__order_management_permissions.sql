-- Permission keys and system roles for the Order Management Control Tower (enforced in order-service OrderAdminController).
INSERT INTO access_permissions(permission_key, module_name, description) VALUES
    ('order:view', 'orders', 'View orders, dashboard, exceptions, returns and saved views within assigned stores'),
    ('order:view-all-stores', 'orders', 'View orders of every store in the business (otherwise only stores named in the token)'),
    ('order:customer:view', 'orders', 'See and search customer name, email, phone and address on orders'),
    ('order:confirm', 'orders', 'Confirm, accept or reject orders'),
    ('order:process', 'orders', 'Process orders, manage fulfillment and shipments, assign operators and handle restaurant preparation'),
    ('order:hold', 'orders', 'Put orders on hold'),
    ('order:release', 'orders', 'Release orders from hold'),
    ('order:cancel', 'orders', 'Request cancellation or cancel orders'),
    ('order:export', 'orders', 'Export order lists to CSV'),
    ('order:bulk', 'orders', 'Run bulk order operations (each action also needs its own permission)'),
    ('order:fulfillment:view', 'orders', 'View order fulfillment and shipment details'),
    ('order:exception:manage', 'orders', 'Run exception scans and assign, escalate and resolve order exceptions'),
    ('order:note:create', 'orders', 'Add internal notes and communication records to orders'),
    ('order:audit:view', 'orders', 'View order audit history'),
    ('order:report:view', 'orders', 'View order reports'),
    ('order:settings', 'orders', 'Change order SLA thresholds and workflow settings')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role) VALUES
    ('ORDER_MANAGER', 'Order Manager', 600, TRUE),
    ('ORDER_OPERATOR', 'Order Operator', 350, TRUE),
    ('ORDER_SUPPORT', 'Order Support Agent', 250, TRUE),
    ('ORDER_ANALYST', 'Order Analyst', 150, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'ADMIN', permission_key FROM access_permissions WHERE permission_key LIKE 'order:%'
ON CONFLICT DO NOTHING;
INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'ORDER_MANAGER', permission_key FROM access_permissions WHERE permission_key LIKE 'order:%'
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('ORDER_OPERATOR','order:view'), ('ORDER_OPERATOR','order:view-all-stores'), ('ORDER_OPERATOR','order:confirm'), ('ORDER_OPERATOR','order:process'),
    ('ORDER_OPERATOR','order:hold'), ('ORDER_OPERATOR','order:release'), ('ORDER_OPERATOR','order:fulfillment:view'), ('ORDER_OPERATOR','order:exception:manage'),
    ('ORDER_OPERATOR','order:note:create'),
    ('ORDER_SUPPORT','order:view'), ('ORDER_SUPPORT','order:view-all-stores'), ('ORDER_SUPPORT','order:customer:view'), ('ORDER_SUPPORT','order:note:create'),
    ('ORDER_SUPPORT','order:fulfillment:view'),
    ('ORDER_ANALYST','order:view'), ('ORDER_ANALYST','order:view-all-stores'), ('ORDER_ANALYST','order:report:view'), ('ORDER_ANALYST','order:export'),
    ('ORDER_ANALYST','order:audit:view')
ON CONFLICT DO NOTHING;
