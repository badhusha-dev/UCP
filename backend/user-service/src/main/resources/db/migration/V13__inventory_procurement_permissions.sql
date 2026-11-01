-- Permission keys and system roles for Inventory & Procurement (enforced in inventory-service and procurement-service).
INSERT INTO access_permissions(permission_key, module_name, description) VALUES
    ('inventory:view', 'inventory', 'View inventory, warehouses, transfers, adjustments, valuation, forecasts and the dashboard'),
    ('inventory:adjust', 'inventory', 'Request stock adjustments (above the configured threshold they wait for approval)'),
    ('inventory:adjust-approve', 'inventory', 'Approve or reject pending stock adjustments (cannot approve own request)'),
    ('inventory:transfer', 'inventory', 'Create transfers and move them through request, pick, dispatch, receive and cancel'),
    ('inventory:transfer-approve', 'inventory', 'Approve or reject stock transfers (cannot approve own request)'),
    ('inventory:audit', 'inventory', 'View the inventory and procurement audit trail'),
    ('inventory:config', 'inventory', 'Change inventory thresholds, tolerances and valuation method'),
    ('inventory:export', 'inventory', 'Export inventory lists to CSV'),
    ('warehouse:view', 'inventory', 'View warehouses, locations and warehouse stock'),
    ('warehouse:manage', 'inventory', 'Create and edit warehouses and locations'),
    ('supplier:view', 'procurement', 'View suppliers, catalogs and performance'),
    ('supplier:manage', 'procurement', 'Create, edit, approve and archive suppliers and their catalog prices'),
    ('purchase-order:view', 'procurement', 'View purchase orders and procurement analytics'),
    ('purchase-order:manage', 'procurement', 'Create, edit, submit and cancel purchase orders; convert replenishment suggestions'),
    ('purchase-order:approve', 'procurement', 'Approve or reject purchase orders (cannot approve own PO)'),
    ('goods-receipt:manage', 'procurement', 'Record goods receipts, inspection results and accepted quantities'),
    ('supplier-return:manage', 'procurement', 'Create and complete supplier returns')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO access_roles(role_key, display_name, hierarchy_level, system_role) VALUES
    ('INVENTORY_MANAGER', 'Inventory Manager', 600, TRUE),
    ('WAREHOUSE_OPERATOR', 'Warehouse Operator', 280, TRUE),
    ('PROCUREMENT_MANAGER', 'Procurement Manager', 580, TRUE),
    ('PROCUREMENT_OFFICER', 'Procurement Officer', 330, TRUE),
    ('INVENTORY_ANALYST', 'Inventory Analyst', 140, TRUE)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key)
SELECT 'ADMIN', permission_key FROM access_permissions WHERE module_name IN ('inventory', 'procurement') AND permission_key LIKE '%:%' AND permission_key IN
    ('inventory:view','inventory:adjust','inventory:adjust-approve','inventory:transfer','inventory:transfer-approve','inventory:audit','inventory:config','inventory:export',
     'warehouse:view','warehouse:manage','supplier:view','supplier:manage','purchase-order:view','purchase-order:manage','purchase-order:approve','goods-receipt:manage','supplier-return:manage')
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permissions(role_key, permission_key) VALUES
    ('INVENTORY_MANAGER','inventory:view'), ('INVENTORY_MANAGER','inventory:adjust'), ('INVENTORY_MANAGER','inventory:adjust-approve'),
    ('INVENTORY_MANAGER','inventory:transfer'), ('INVENTORY_MANAGER','inventory:transfer-approve'), ('INVENTORY_MANAGER','inventory:audit'),
    ('INVENTORY_MANAGER','inventory:config'), ('INVENTORY_MANAGER','inventory:export'), ('INVENTORY_MANAGER','warehouse:view'), ('INVENTORY_MANAGER','warehouse:manage'),
    ('INVENTORY_MANAGER','goods-receipt:manage'), ('INVENTORY_MANAGER','supplier:view'), ('INVENTORY_MANAGER','purchase-order:view'),
    ('WAREHOUSE_OPERATOR','inventory:view'), ('WAREHOUSE_OPERATOR','inventory:adjust'), ('WAREHOUSE_OPERATOR','inventory:transfer'),
    ('WAREHOUSE_OPERATOR','warehouse:view'), ('WAREHOUSE_OPERATOR','goods-receipt:manage'), ('WAREHOUSE_OPERATOR','purchase-order:view'),
    ('PROCUREMENT_MANAGER','supplier:view'), ('PROCUREMENT_MANAGER','supplier:manage'), ('PROCUREMENT_MANAGER','purchase-order:view'),
    ('PROCUREMENT_MANAGER','purchase-order:manage'), ('PROCUREMENT_MANAGER','purchase-order:approve'), ('PROCUREMENT_MANAGER','supplier-return:manage'),
    ('PROCUREMENT_MANAGER','goods-receipt:manage'), ('PROCUREMENT_MANAGER','inventory:view'), ('PROCUREMENT_MANAGER','inventory:export'),
    ('PROCUREMENT_OFFICER','supplier:view'), ('PROCUREMENT_OFFICER','purchase-order:view'), ('PROCUREMENT_OFFICER','purchase-order:manage'), ('PROCUREMENT_OFFICER','inventory:view'),
    ('INVENTORY_ANALYST','inventory:view'), ('INVENTORY_ANALYST','inventory:export'), ('INVENTORY_ANALYST','warehouse:view'), ('INVENTORY_ANALYST','supplier:view'), ('INVENTORY_ANALYST','purchase-order:view')
ON CONFLICT DO NOTHING;
