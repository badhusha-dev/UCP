-- Customers track their own deliveries through a narrowly scoped grant. The permission is deliberately in its own module so
-- the DELIVERY_ADMIN "all delivery permissions" convention never silently widens it, and the role carries nothing else.
INSERT INTO access_permissions(permission_key, module_name, description) VALUES
    ('delivery:track:own', 'delivery-recipient', 'Track own deliveries and obtain the one-time handover code')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role) VALUES
    ('DELIVERY_RECIPIENT', 'Delivery Recipient', 100, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('DELIVERY_RECIPIENT', 'delivery:track:own')
ON CONFLICT DO NOTHING;
