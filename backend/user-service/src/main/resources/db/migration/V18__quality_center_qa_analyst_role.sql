-- QA_ANALYST: dedicated Quality Center role.
-- V5 granted qa:read/run/cancel only to SUPER_ADMIN and ADMIN, leaving QUALITY_CENTER
-- (already ACTIVE since V6) with no operational role for QA staff.
INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role)
VALUES ('QA_ANALYST', 'QA Analyst', 160, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key)
VALUES ('QA_ANALYST', 'qa:read')
ON CONFLICT DO NOTHING;

UPDATE application_registry
SET metadata    = metadata || '{"dedicatedRoles":["QA_ANALYST","ADMIN"]}'::jsonb,
    updated_at  = CURRENT_TIMESTAMP
WHERE application_code = 'QUALITY_CENTER';
