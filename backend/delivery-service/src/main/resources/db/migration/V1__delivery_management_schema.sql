CREATE TABLE delivery_zone (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(120) NOT NULL,
    business_type VARCHAR(24) NOT NULL CHECK (business_type IN ('RETAIL','RESTAURANT','BOTH')),
    postal_prefix VARCHAR(24) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, code),
    CHECK (length(trim(postal_prefix)) > 0)
);
CREATE INDEX idx_delivery_zone_match ON delivery_zone(tenant_id, active, business_type, postal_prefix);

CREATE TABLE delivery_partner_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    vehicle_type VARCHAR(32) NOT NULL,
    capacity_kg NUMERIC(10,2) NOT NULL CHECK (capacity_kg > 0),
    max_active_deliveries INTEGER NOT NULL DEFAULT 1 CHECK (max_active_deliveries BETWEEN 1 AND 20),
    verification_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING','APPROVED','REJECTED')),
    document_status VARCHAR(24) NOT NULL DEFAULT 'NOT_SUBMITTED' CHECK (document_status IN ('NOT_SUBMITTED','SUBMITTED','REVIEW_REQUIRED','ACCEPTED','REJECTED')),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    availability_status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' CHECK (availability_status IN ('ONLINE','OFFLINE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, user_id),
    UNIQUE (tenant_id, email)
);
CREATE INDEX idx_delivery_partner_dispatch ON delivery_partner_profile(tenant_id, active, verification_status, availability_status);

CREATE TABLE delivery_partner_capability (
    tenant_id UUID NOT NULL,
    partner_id UUID NOT NULL REFERENCES delivery_partner_profile(id) ON DELETE CASCADE,
    capability VARCHAR(48) NOT NULL,
    PRIMARY KEY (tenant_id, partner_id, capability)
);
CREATE TABLE delivery_partner_zone (
    tenant_id UUID NOT NULL,
    partner_id UUID NOT NULL REFERENCES delivery_partner_profile(id) ON DELETE CASCADE,
    zone_id UUID NOT NULL REFERENCES delivery_zone(id),
    PRIMARY KEY (tenant_id, partner_id, zone_id)
);

CREATE TABLE delivery (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_reference VARCHAR(128) NOT NULL,
    recipient_user_id BIGINT,
    business_type VARCHAR(24) NOT NULL CHECK (business_type IN ('RETAIL','RESTAURANT')),
    pickup_name VARCHAR(160) NOT NULL,
    pickup_address TEXT NOT NULL,
    dropoff_name VARCHAR(160) NOT NULL,
    dropoff_address TEXT NOT NULL,
    postal_code VARCHAR(24) NOT NULL,
    required_capability VARCHAR(48),
    weight_kg NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (weight_kg >= 0),
    item_count INTEGER NOT NULL DEFAULT 1 CHECK (item_count > 0),
    scheduled_for TIMESTAMPTZ,
    pickup_ready BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','READY_FOR_DISPATCH','ASSIGNED','ARRIVED_AT_PICKUP','PICKED_UP','IN_TRANSIT','ARRIVED_AT_DROPOFF','DELIVERED','CANCELLED','PICKUP_FAILED','DELIVERY_FAILED','RETURN_TO_ORIGIN','RETURNED')),
    assigned_partner_id UUID REFERENCES delivery_partner_profile(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    cancel_reason TEXT,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, order_reference)
);
CREATE INDEX idx_delivery_tenant_status_created ON delivery(tenant_id, status, created_at DESC);
CREATE INDEX idx_delivery_tenant_schedule ON delivery(tenant_id, scheduled_for) WHERE scheduled_for IS NOT NULL;
CREATE INDEX idx_delivery_partner_active ON delivery(tenant_id, assigned_partner_id, status);

CREATE TABLE delivery_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL REFERENCES delivery(id),
    partner_id UUID NOT NULL REFERENCES delivery_partner_profile(id),
    assigned_by BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('OFFERED','ACCEPTED','REJECTED','EXPIRED','REASSIGNED','COMPLETED')),
    reason TEXT,
    offered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_delivery_active_assignment ON delivery_assignment(tenant_id, delivery_id) WHERE active=TRUE;
CREATE INDEX idx_delivery_assignment_partner ON delivery_assignment(tenant_id, partner_id, status, active);

CREATE TABLE delivery_status_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL REFERENCES delivery(id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    reason TEXT,
    actor_user_id BIGINT NOT NULL,
    actor_email VARCHAR(255) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX idx_delivery_history_timeline ON delivery_status_history(tenant_id, delivery_id, created_at DESC);

CREATE TABLE delivery_location_update (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL REFERENCES delivery(id) ON DELETE CASCADE,
    partner_id UUID NOT NULL REFERENCES delivery_partner_profile(id),
    latitude NUMERIC(9,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    accuracy_m NUMERIC(8,2) CHECK (accuracy_m IS NULL OR accuracy_m >= 0),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_delivery_location_recent ON delivery_location_update(tenant_id, delivery_id, recorded_at DESC);

CREATE TABLE delivery_proof (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL UNIQUE REFERENCES delivery(id),
    method VARCHAR(24) NOT NULL CHECK (method IN ('RECIPIENT_NAME','PHOTO_REFERENCE','SIGNATURE_REFERENCE','PIN_CONFIRMED')),
    recipient_name VARCHAR(160),
    evidence_reference VARCHAR(500),
    note VARCHAR(1000),
    captured_by BIGINT NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE delivery_incident (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL REFERENCES delivery(id),
    type VARCHAR(32) NOT NULL CHECK (type IN ('CUSTOMER_UNAVAILABLE','ADDRESS_ISSUE','VEHICLE_ISSUE','SAFETY','DAMAGED_ITEM','OTHER')),
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED')),
    reported_by BIGINT NOT NULL,
    resolved_by BIGINT,
    resolution VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_delivery_incidents_open ON delivery_incident(tenant_id, status, created_at DESC);

CREATE TABLE delivery_trip (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    partner_id UUID NOT NULL REFERENCES delivery_partner_profile(id),
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','ACTIVE','COMPLETED','CANCELLED')),
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE delivery_trip_stop (
    tenant_id UUID NOT NULL,
    trip_id UUID NOT NULL REFERENCES delivery_trip(id) ON DELETE CASCADE,
    delivery_id UUID NOT NULL REFERENCES delivery(id),
    stop_order INTEGER NOT NULL CHECK (stop_order > 0),
    PRIMARY KEY (tenant_id, trip_id, delivery_id),
    UNIQUE (tenant_id, trip_id, stop_order)
);

CREATE TABLE delivery_pricing_policy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    business_type VARCHAR(24) NOT NULL CHECK (business_type IN ('RETAIL','RESTAURANT')),
    base_earning NUMERIC(12,2) NOT NULL CHECK (base_earning >= 0),
    per_item_earning NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (per_item_earning >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, business_type)
);
CREATE TABLE delivery_partner_earning (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL UNIQUE REFERENCES delivery(id),
    partner_id UUID NOT NULL REFERENCES delivery_partner_profile(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL,
    settlement_status VARCHAR(16) NOT NULL DEFAULT 'UNSETTLED' CHECK (settlement_status IN ('UNSETTLED','SETTLED','HELD')),
    settlement_reference VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE delivery_configuration (
    tenant_id UUID NOT NULL,
    config_key VARCHAR(64) NOT NULL,
    config_value TEXT NOT NULL,
    updated_by BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, config_key)
);
CREATE TABLE delivery_audit_event (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_email VARCHAR(255) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(48) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_delivery_audit_tenant_time ON delivery_audit_event(tenant_id, created_at DESC);

CREATE TABLE delivery_idempotency (
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, idempotency_key)
);
CREATE TABLE delivery_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','IN_FLIGHT','PUBLISHED')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_delivery_outbox_ready ON delivery_outbox(status, next_attempt_at, created_at);

INSERT INTO delivery_configuration(tenant_id, config_key, config_value, updated_by)
VALUES
    ('00000000-0000-4000-8000-000000000001','location_retention_days','7',1),
    ('00000000-0000-4000-8000-000000000001','assignment_timeout_minutes','5',1)
ON CONFLICT (tenant_id, config_key) DO NOTHING;
