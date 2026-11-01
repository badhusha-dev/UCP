INSERT INTO access_permissions(permission_key, module_name, description) VALUES
    ('qa:read', 'quality-center', 'View QA catalog, runs, results, and reports'),
    ('qa:run', 'quality-center', 'Start approved local QA test suites'),
    ('qa:cancel', 'quality-center', 'Cancel an active QA test run')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('SUPER_ADMIN', 'qa:read'), ('SUPER_ADMIN', 'qa:run'), ('SUPER_ADMIN', 'qa:cancel'),
    ('ADMIN', 'qa:read'), ('ADMIN', 'qa:run'), ('ADMIN', 'qa:cancel')
ON CONFLICT DO NOTHING;
