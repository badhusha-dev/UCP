-- Advanced Pricing & Promotions (E-commerce Admin). Every table is tenant-scoped; store_id '*' means "all stores".
-- Written in the SQL subset shared by PostgreSQL and H2 (PostgreSQL mode) so the integration tests execute this exact text.
-- Configuration is plain validated columns: no expressions, scripts or SQL fragments are ever stored.

CREATE TABLE IF NOT EXISTS pricing_settings (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id VARCHAR(64) NOT NULL DEFAULT '*',
    default_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    supported_currencies VARCHAR(120) NOT NULL DEFAULT 'USD,EUR,GBP,INR',
    max_discount_percent NUMERIC(5,2) NOT NULL DEFAULT 50.00,
    min_margin_percent NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    default_campaign_days INTEGER NOT NULL DEFAULT 14,
    coupon_bulk_max INTEGER NOT NULL DEFAULT 1000,
    coupon_per_customer_max INTEGER NOT NULL DEFAULT 5,
    approval_threshold_percent NUMERIC(5,2) NOT NULL DEFAULT 20.00,
    stacking_policy VARCHAR(24) NOT NULL DEFAULT 'FLAGGED_ONLY',
    supported_types VARCHAR(160) NOT NULL DEFAULT 'PERCENTAGE,FIXED_AMOUNT,BUY_X_GET_Y,BUNDLE,QUANTITY_TIER',
    version INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_pricing_settings_scope UNIQUE (tenant_id, store_id),
    CONSTRAINT ck_ps_max_discount CHECK (max_discount_percent > 0 AND max_discount_percent <= 100),
    CONSTRAINT ck_ps_margin CHECK (min_margin_percent >= 0 AND min_margin_percent < 100),
    CONSTRAINT ck_ps_days CHECK (default_campaign_days BETWEEN 1 AND 366),
    CONSTRAINT ck_ps_bulk CHECK (coupon_bulk_max BETWEEN 1 AND 10000),
    CONSTRAINT ck_ps_per_customer CHECK (coupon_per_customer_max BETWEEN 1 AND 1000),
    CONSTRAINT ck_ps_threshold CHECK (approval_threshold_percent >= 0 AND approval_threshold_percent <= 100),
    CONSTRAINT ck_ps_stacking CHECK (stacking_policy IN ('NO_STACKING', 'FLAGGED_ONLY', 'STACK_ALL'))
);

CREATE TABLE IF NOT EXISTS product_prices (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id VARCHAR(64) NOT NULL DEFAULT '*',
    product_id BIGINT NOT NULL REFERENCES product(id),
    variant_id BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    base_price NUMERIC(14,2) NOT NULL,
    sale_price NUMERIC(14,2),
    cost_price NUMERIC(14,2),
    min_selling_price NUMERIC(14,2),
    max_discount_percent NUMERIC(5,2),
    tax_inclusive BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_product_price_scope UNIQUE (tenant_id, store_id, product_id, variant_id, currency),
    CONSTRAINT ck_pp_base CHECK (base_price > 0),
    CONSTRAINT ck_pp_sale CHECK (sale_price IS NULL OR (sale_price > 0 AND sale_price <= base_price)),
    CONSTRAINT ck_pp_cost CHECK (cost_price IS NULL OR cost_price >= 0),
    CONSTRAINT ck_pp_min_selling CHECK (min_selling_price IS NULL OR min_selling_price > 0),
    CONSTRAINT ck_pp_max_discount CHECK (max_discount_percent IS NULL OR (max_discount_percent > 0 AND max_discount_percent <= 100)),
    CONSTRAINT ck_pp_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_pp_window CHECK (effective_to IS NULL OR effective_to > effective_from)
);
CREATE INDEX IF NOT EXISTS idx_pp_tenant_product ON product_prices(tenant_id, product_id);

CREATE TABLE IF NOT EXISTS price_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id VARCHAR(64) NOT NULL DEFAULT '*',
    product_price_id BIGINT NOT NULL REFERENCES product_prices(id),
    product_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    old_base_price NUMERIC(14,2),
    new_base_price NUMERIC(14,2) NOT NULL,
    old_sale_price NUMERIC(14,2),
    new_sale_price NUMERIC(14,2),
    old_cost_price NUMERIC(14,2),
    new_cost_price NUMERIC(14,2),
    change_source VARCHAR(16) NOT NULL,
    reason VARCHAR(255),
    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ph_source CHECK (change_source IN ('MANUAL', 'BULK', 'SCHEDULED', 'IMPORT', 'ROLLBACK'))
);
CREATE INDEX IF NOT EXISTS idx_ph_tenant_product ON price_history(tenant_id, product_id, changed_at DESC);

CREATE TABLE IF NOT EXISTS scheduled_prices (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id VARCHAR(64) NOT NULL DEFAULT '*',
    product_id BIGINT NOT NULL REFERENCES product(id),
    variant_id BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    new_base_price NUMERIC(14,2) NOT NULL,
    new_sale_price NUMERIC(14,2),
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    reason VARCHAR(255),
    applied_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sp_status CHECK (status IN ('PENDING_APPROVAL', 'SCHEDULED', 'APPLIED', 'CANCELLED', 'REJECTED')),
    CONSTRAINT ck_sp_base CHECK (new_base_price > 0),
    CONSTRAINT ck_sp_sale CHECK (new_sale_price IS NULL OR (new_sale_price > 0 AND new_sale_price <= new_base_price)),
    CONSTRAINT ck_sp_window CHECK (effective_to IS NULL OR effective_to > effective_from)
);
CREATE INDEX IF NOT EXISTS idx_sp_due ON scheduled_prices(tenant_id, status, effective_from);

CREATE TABLE IF NOT EXISTS promotion_campaigns (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id VARCHAR(64) NOT NULL DEFAULT '*',
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    promotion_type VARCHAR(20) NOT NULL,
    scope VARCHAR(16) NOT NULL DEFAULT 'PRODUCT',
    automatic BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 100,
    stackable BOOLEAN NOT NULL DEFAULT FALSE,
    currency VARCHAR(3) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    min_order_value NUMERIC(14,2),
    max_discount_amount NUMERIC(14,2),
    usage_limit_total INTEGER,
    usage_limit_per_customer INTEGER,
    usage_count INTEGER NOT NULL DEFAULT 0,
    customer_segment VARCHAR(64),
    first_order_only BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pc_type CHECK (promotion_type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'BUY_X_GET_Y', 'BUNDLE', 'QUANTITY_TIER')),
    CONSTRAINT ck_pc_scope CHECK (scope IN ('PRODUCT', 'CATEGORY', 'STORE_WIDE')),
    CONSTRAINT ck_pc_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PUBLISHED', 'PAUSED', 'CANCELLED', 'REJECTED')),
    CONSTRAINT ck_pc_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_pc_priority CHECK (priority BETWEEN 0 AND 10000),
    CONSTRAINT ck_pc_min_order CHECK (min_order_value IS NULL OR min_order_value >= 0),
    CONSTRAINT ck_pc_max_discount CHECK (max_discount_amount IS NULL OR max_discount_amount > 0),
    CONSTRAINT ck_pc_limits CHECK ((usage_limit_total IS NULL OR usage_limit_total > 0)
        AND (usage_limit_per_customer IS NULL OR usage_limit_per_customer > 0) AND usage_count >= 0)
);
CREATE INDEX IF NOT EXISTS idx_pc_tenant_status ON promotion_campaigns(tenant_id, status, starts_at, ends_at);

CREATE TABLE IF NOT EXISTS promotion_rules (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id BIGINT NOT NULL REFERENCES promotion_campaigns(id),
    rule_role VARCHAR(8) NOT NULL DEFAULT 'TRIGGER',
    effect VARCHAR(8) NOT NULL DEFAULT 'INCLUDE',
    target_type VARCHAR(10) NOT NULL,
    target_id BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pr_role CHECK (rule_role IN ('TRIGGER', 'REWARD')),
    CONSTRAINT ck_pr_effect CHECK (effect IN ('INCLUDE', 'EXCLUDE')),
    CONSTRAINT ck_pr_target CHECK (target_type IN ('PRODUCT', 'CATEGORY', 'ALL')),
    CONSTRAINT ux_pr_target UNIQUE (campaign_id, rule_role, effect, target_type, target_id)
);
CREATE INDEX IF NOT EXISTS idx_pr_tenant_campaign ON promotion_rules(tenant_id, campaign_id);

CREATE TABLE IF NOT EXISTS promotion_conditions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id BIGINT NOT NULL REFERENCES promotion_campaigns(id),
    condition_type VARCHAR(20) NOT NULL,
    numeric_value NUMERIC(14,2),
    text_value VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pcd_type CHECK (condition_type IN ('MIN_ORDER_VALUE', 'MIN_QUANTITY', 'CUSTOMER_SEGMENT', 'FIRST_ORDER')),
    CONSTRAINT ux_pcd UNIQUE (campaign_id, condition_type)
);
CREATE INDEX IF NOT EXISTS idx_pcd_tenant_campaign ON promotion_conditions(tenant_id, campaign_id);

CREATE TABLE IF NOT EXISTS promotion_rewards (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id BIGINT NOT NULL REFERENCES promotion_campaigns(id),
    reward_type VARCHAR(20) NOT NULL,
    percent_value NUMERIC(5,2),
    amount_value NUMERIC(14,2),
    buy_quantity INTEGER,
    get_quantity INTEGER,
    min_quantity INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_prw_type CHECK (reward_type IN ('PERCENT_OFF', 'AMOUNT_OFF', 'BUY_X_GET_Y', 'BUNDLE_PRICE', 'TIER_PERCENT')),
    CONSTRAINT ck_prw_percent CHECK (percent_value IS NULL OR (percent_value > 0 AND percent_value <= 100)),
    CONSTRAINT ck_prw_amount CHECK (amount_value IS NULL OR amount_value > 0),
    CONSTRAINT ck_prw_qty CHECK ((buy_quantity IS NULL OR buy_quantity > 0) AND (get_quantity IS NULL OR get_quantity > 0)
        AND (min_quantity IS NULL OR min_quantity > 0))
);
CREATE INDEX IF NOT EXISTS idx_prw_tenant_campaign ON promotion_rewards(tenant_id, campaign_id);

CREATE TABLE IF NOT EXISTS coupon_codes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id BIGINT NOT NULL REFERENCES promotion_campaigns(id),
    code VARCHAR(40) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    usage_limit INTEGER,
    per_customer_limit INTEGER,
    redeemed_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE,
    restricted_customer_id VARCHAR(64),
    batch_id VARCHAR(40),
    version INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_coupon_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_cc_status CHECK (status IN ('ACTIVE', 'PAUSED', 'REVOKED')),
    CONSTRAINT ck_cc_limits CHECK ((usage_limit IS NULL OR usage_limit > 0) AND (per_customer_limit IS NULL OR per_customer_limit > 0)
        AND redeemed_count >= 0)
);
CREATE INDEX IF NOT EXISTS idx_cc_tenant_campaign ON coupon_codes(tenant_id, campaign_id);

CREATE TABLE IF NOT EXISTS coupon_redemptions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    coupon_id BIGINT NOT NULL REFERENCES coupon_codes(id),
    campaign_id BIGINT NOT NULL REFERENCES promotion_campaigns(id),
    customer_id VARCHAR(64) NOT NULL,
    order_ref VARCHAR(64) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'REDEEMED',
    redeemed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ux_redemption_order UNIQUE (tenant_id, coupon_id, order_ref),
    CONSTRAINT ck_cr_status CHECK (status IN ('REDEEMED', 'REVERSED')),
    CONSTRAINT ck_cr_amount CHECK (discount_amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_cr_customer ON coupon_redemptions(tenant_id, coupon_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_cr_campaign ON coupon_redemptions(tenant_id, campaign_id, redeemed_at DESC);

CREATE TABLE IF NOT EXISTS promotion_approvals (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    subject_id BIGINT NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(255),
    requested_by BIGINT NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by BIGINT,
    decided_at TIMESTAMP WITH TIME ZONE,
    decision_note VARCHAR(255),
    CONSTRAINT ck_pa_subject CHECK (subject_type IN ('CAMPAIGN', 'SCHEDULED_PRICE')),
    CONSTRAINT ck_pa_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);
CREATE INDEX IF NOT EXISTS idx_pa_tenant_status ON promotion_approvals(tenant_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_pa_subject ON promotion_approvals(tenant_id, subject_type, subject_id);

CREATE TABLE IF NOT EXISTS promotion_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(24) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_email VARCHAR(255),
    detail TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pal_tenant_time ON promotion_audit_logs(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pal_entity ON promotion_audit_logs(tenant_id, entity_type, entity_id);

-- One row per order that consumed promotions; the primary key makes redemption idempotent per order and tenant.
CREATE TABLE IF NOT EXISTS promotion_order_applications (
    tenant_id UUID NOT NULL,
    order_ref VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64),
    currency VARCHAR(3),
    subtotal NUMERIC(14,2),
    discount_total NUMERIC(14,2),
    total_amount NUMERIC(14,2),
    result TEXT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'APPLIED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (tenant_id, order_ref),
    CONSTRAINT ck_poa_status CHECK (status IN ('APPLIED', 'REVERSED'))
);
