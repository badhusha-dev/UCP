-- Storefront Management & Content Studio. Tenant-scoped; SQL kept in the PostgreSQL/H2 common subset (tests run this exact text).
-- Content is stored as validated structured JSON (typed blocks only: no HTML or scripts). Large binaries live in object storage, not here.

CREATE TABLE IF NOT EXISTS storefronts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id VARCHAR(64) NOT NULL DEFAULT 'default',
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(63) NOT NULL,
    storefront_type VARCHAR(12) NOT NULL DEFAULT 'RETAIL',
    default_locale VARCHAR(12) NOT NULL DEFAULT 'en',
    supported_locales VARCHAR(120) NOT NULL DEFAULT 'en',
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    version INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_storefront_slug UNIQUE (slug),
    CONSTRAINT ux_storefront_tenant_store UNIQUE (tenant_id, store_id),
    CONSTRAINT ck_sf_type CHECK (storefront_type IN ('RETAIL', 'RESTAURANT')),
    CONSTRAINT ck_sf_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);
CREATE INDEX IF NOT EXISTS idx_sf_tenant ON storefronts(tenant_id);

CREATE TABLE IF NOT EXISTS storefront_documents (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    storefront_id BIGINT NOT NULL REFERENCES storefronts(id),
    doc_type VARCHAR(16) NOT NULL,
    slug VARCHAR(80) NOT NULL DEFAULT '',
    title VARCHAR(160) NOT NULL,
    published_version_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_sf_doc UNIQUE (storefront_id, doc_type, slug),
    CONSTRAINT ck_sfd_type CHECK (doc_type IN ('HOMEPAGE', 'LANDING', 'THEME', 'NAVIGATION', 'SETTINGS'))
);
CREATE INDEX IF NOT EXISTS idx_sfd_tenant_sf ON storefront_documents(tenant_id, storefront_id);

-- Every edit lives in a version. A version's content may change only while it is DRAFT; PUBLISHED content is immutable.
CREATE TABLE IF NOT EXISTS storefront_versions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    document_id BIGINT NOT NULL REFERENCES storefront_documents(id),
    version_no INTEGER NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    change_note VARCHAR(255),
    source_version_id BIGINT,
    scheduled_publish_at TIMESTAMP WITH TIME ZONE,
    submitted_by BIGINT,
    submitted_at TIMESTAMP WITH TIME ZONE,
    approved_by BIGINT,
    approved_at TIMESTAMP WITH TIME ZONE,
    published_by BIGINT,
    published_at TIMESTAMP WITH TIME ZONE,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_sfv_doc_no UNIQUE (document_id, version_no),
    CONSTRAINT ck_sfv_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_sfv_tenant_doc ON storefront_versions(tenant_id, document_id, version_no DESC);
CREATE INDEX IF NOT EXISTS idx_sfv_due ON storefront_versions(status, scheduled_publish_at);

CREATE TABLE IF NOT EXISTS storefront_approval_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    version_id BIGINT NOT NULL REFERENCES storefront_versions(id),
    action VARCHAR(24) NOT NULL,
    actor_id BIGINT NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sfah_version ON storefront_approval_history(tenant_id, version_id);

CREATE TABLE IF NOT EXISTS storefront_media (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    storefront_id BIGINT NOT NULL REFERENCES storefronts(id),
    public_id VARCHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    file_name VARCHAR(160) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    alt_text VARCHAR(255) NOT NULL,
    storage_key VARCHAR(200) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_sfm_public UNIQUE (public_id),
    CONSTRAINT ck_sfm_kind CHECK (kind IN ('LOGO', 'FAVICON', 'BANNER_DESKTOP', 'BANNER_MOBILE', 'PRODUCT', 'SOCIAL', 'GENERAL')),
    CONSTRAINT ck_sfm_size CHECK (size_bytes > 0 AND width > 0 AND height > 0)
);
CREATE INDEX IF NOT EXISTS idx_sfm_tenant_sf ON storefront_media(tenant_id, storefront_id);

CREATE TABLE IF NOT EXISTS storefront_banners (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    storefront_id BIGINT NOT NULL REFERENCES storefronts(id),
    name VARCHAR(160) NOT NULL,
    placement VARCHAR(12) NOT NULL DEFAULT 'HERO',
    desktop_media_id BIGINT NOT NULL REFERENCES storefront_media(id),
    mobile_media_id BIGINT REFERENCES storefront_media(id),
    alt_text VARCHAR(255) NOT NULL,
    destination_url VARCHAR(500),
    campaign_id BIGINT,
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    priority INTEGER NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sfb_placement CHECK (placement IN ('HERO', 'PROMO', 'STRIP')),
    CONSTRAINT ck_sfb_window CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at),
    CONSTRAINT ck_sfb_priority CHECK (priority BETWEEN 0 AND 10000)
);
CREATE INDEX IF NOT EXISTS idx_sfb_tenant_sf ON storefront_banners(tenant_id, storefront_id, active);

CREATE TABLE IF NOT EXISTS storefront_domains (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    storefront_id BIGINT NOT NULL REFERENCES storefronts(id),
    hostname VARCHAR(253) NOT NULL,
    verification_token VARCHAR(64) NOT NULL,
    verification_status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMP WITH TIME ZONE,
    last_check_at TIMESTAMP WITH TIME ZONE,
    last_check_message VARCHAR(255),
    ssl_status VARCHAR(12) NOT NULL DEFAULT 'NOT_CHECKED',
    ssl_checked_at TIMESTAMP WITH TIME ZONE,
    ssl_expires_at TIMESTAMP WITH TIME ZONE,
    ssl_message VARCHAR(255),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_sfdom_host UNIQUE (hostname),
    CONSTRAINT ck_sfdom_verif CHECK (verification_status IN ('PENDING', 'VERIFIED', 'FAILED')),
    CONSTRAINT ck_sfdom_ssl CHECK (ssl_status IN ('NOT_CHECKED', 'ACTIVE', 'FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_sfdom_tenant_sf ON storefront_domains(tenant_id, storefront_id);

CREATE TABLE IF NOT EXISTS storefront_theme_presets (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    content TEXT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_sftp_name UNIQUE (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS storefront_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    storefront_id BIGINT,
    entity_type VARCHAR(24) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_email VARCHAR(255),
    detail TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sfal_tenant_time ON storefront_audit_logs(tenant_id, created_at DESC);
