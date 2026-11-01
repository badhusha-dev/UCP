-- Permission keys and system roles for Returns & Refund Management (enforced in order-service ReturnAdminController).
INSERT INTO access_permissions(permission_key, module_name, description) VALUES
    ('return:view', 'returns', 'View returns, refunds, exchanges, exceptions, policies and the returns dashboard within assigned stores'),
    ('return:view-customer', 'returns', 'See customer name, email, phone and address on return requests (otherwise masked)'),
    ('return:create', 'returns', 'Create return, exchange or claim requests on behalf of a customer'),
    ('return:review', 'returns', 'Move requests into review, request information, add internal notes, assign and cancel'),
    ('return:approve', 'returns', 'Approve or partially approve return requests'),
    ('return:reject', 'returns', 'Reject return requests (a reason is mandatory)'),
    ('return:pickup', 'returns', 'Schedule and cancel reverse pickups'),
    ('return:receive', 'returns', 'Mark returned goods received and queue them for inspection'),
    ('return:inspect', 'returns', 'Record warehouse inspection results'),
    ('return:refund-process', 'returns', 'Calculate, approve (below threshold) and send refunds to Payment'),
    ('return:refund-approve', 'returns', 'Approve refunds above the approval threshold (cannot approve own calculation)'),
    ('return:exchange', 'returns', 'Create, approve and fulfill exchanges'),
    ('return:policy-manage', 'returns', 'Create and edit return policy drafts and submit them for review'),
    ('return:policy-approve', 'returns', 'Approve, publish and roll back return policies (cannot review own draft)'),
    ('return:config', 'returns', 'Change refund and exception thresholds'),
    ('return:exception-manage', 'returns', 'Assign, escalate, resolve and dismiss return exceptions and fraud-risk flags'),
    ('return:audit-view', 'returns', 'View the returns audit history'),
    ('return:analytics-view', 'returns', 'View return analytics'),
    ('return:export', 'returns', 'Export return lists to CSV')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role) VALUES
    ('RETURNS_MANAGER', 'Returns Manager', 620, TRUE),
    ('RETURNS_AGENT', 'Returns Agent', 340, TRUE),
    ('WAREHOUSE_INSPECTOR', 'Warehouse Inspector', 260, TRUE),
    ('REFUND_APPROVER', 'Refund Approver', 540, TRUE),
    ('RETURNS_ANALYST', 'Returns Analyst', 150, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'ADMIN', permission_key FROM access_permissions WHERE permission_key LIKE 'return:%'
ON CONFLICT DO NOTHING;
INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'RETURNS_MANAGER', permission_key FROM access_permissions WHERE permission_key LIKE 'return:%'
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('RETURNS_MANAGER','order:view'), ('RETURNS_MANAGER','order:view-all-stores'),
    ('RETURNS_AGENT','order:view'), ('RETURNS_AGENT','order:view-all-stores'),
    ('RETURNS_AGENT','return:view'), ('RETURNS_AGENT','return:view-customer'), ('RETURNS_AGENT','return:create'), ('RETURNS_AGENT','return:review'), ('RETURNS_AGENT','return:approve'),
    ('RETURNS_AGENT','return:reject'), ('RETURNS_AGENT','return:pickup'), ('RETURNS_AGENT','return:receive'), ('RETURNS_AGENT','return:exchange'),
    ('WAREHOUSE_INSPECTOR','order:view'), ('WAREHOUSE_INSPECTOR','order:view-all-stores'),
    ('WAREHOUSE_INSPECTOR','return:view'), ('WAREHOUSE_INSPECTOR','return:receive'), ('WAREHOUSE_INSPECTOR','return:inspect'),
    ('REFUND_APPROVER','order:view'), ('REFUND_APPROVER','order:view-all-stores'),
    ('REFUND_APPROVER','return:view'), ('REFUND_APPROVER','return:refund-process'), ('REFUND_APPROVER','return:refund-approve'), ('REFUND_APPROVER','return:analytics-view'), ('REFUND_APPROVER','return:audit-view'),
    ('RETURNS_ANALYST','order:view'), ('RETURNS_ANALYST','order:view-all-stores'),
    ('RETURNS_ANALYST','return:view'), ('RETURNS_ANALYST','return:analytics-view'), ('RETURNS_ANALYST','return:audit-view'), ('RETURNS_ANALYST','return:export')
ON CONFLICT DO NOTHING;
