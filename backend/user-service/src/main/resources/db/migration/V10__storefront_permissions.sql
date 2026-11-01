-- Permission keys and system roles for Storefront Management & Content Studio (enforced in product-service StorefrontAdminController).
INSERT INTO access_permissions(permission_key, module_name, description) VALUES
    ('storefront:read', 'storefront', 'View storefronts, pages, versions, media, banners, domains and previews'),
    ('storefront:manage', 'storefront', 'Create storefronts and rename them'),
    ('storefront:edit', 'storefront', 'Edit drafts, pages, banners and navigation, and submit versions for review'),
    ('storefront:media', 'storefront', 'Upload and delete storefront images'),
    ('storefront:approve', 'storefront', 'Approve or send back submitted storefront versions'),
    ('storefront:publish', 'storefront', 'Publish, schedule, unpublish and roll back storefront content'),
    ('storefront:domain', 'storefront', 'Manage custom domains, verification and SSL checks'),
    ('storefront:audit', 'storefront', 'View storefront audit history')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role) VALUES
    ('STOREFRONT_MANAGER', 'Storefront Manager', 450, TRUE),
    ('CONTENT_EDITOR', 'Content Editor', 300, TRUE),
    ('STOREFRONT_PUBLISHER', 'Storefront Publisher', 550, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'ADMIN', permission_key FROM access_permissions WHERE module_name = 'storefront'
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('STOREFRONT_MANAGER','storefront:read'), ('STOREFRONT_MANAGER','storefront:manage'), ('STOREFRONT_MANAGER','storefront:edit'),
    ('STOREFRONT_MANAGER','storefront:media'), ('STOREFRONT_MANAGER','storefront:domain'),
    ('CONTENT_EDITOR','storefront:read'), ('CONTENT_EDITOR','storefront:edit'), ('CONTENT_EDITOR','storefront:media'),
    ('STOREFRONT_PUBLISHER','storefront:read'), ('STOREFRONT_PUBLISHER','storefront:approve'), ('STOREFRONT_PUBLISHER','storefront:publish'),
    ('STOREFRONT_PUBLISHER','storefront:audit')
ON CONFLICT DO NOTHING;
