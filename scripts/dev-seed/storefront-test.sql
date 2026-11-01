-- Dev-only seed: one storefront for test tenant A (product_db), used to verify storefront checkout on the local stack.
-- Run: docker exec -i ecommerce-platform-postgres-1 psql -v ON_ERROR_STOP=1 -U ecommerce_admin -d product_db < scripts/dev-seed/storefront-test.sql
-- Remove: DELETE FROM storefronts WHERE slug='ucp-test-acct-a-shop';
INSERT INTO storefronts(tenant_id, store_id, name, slug, storefront_type, status)
VALUES ('a0c0a0c0-0000-4000-8000-0000000000a1', 'test-main', 'UCP Test Tenant A Shop', 'ucp-test-acct-a-shop', 'RETAIL', 'ACTIVE')
ON CONFLICT (slug) DO NOTHING;
