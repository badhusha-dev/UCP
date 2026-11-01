-- B-21: JWT logout revocation — stores revoked token JTIs until they naturally expire.
CREATE TABLE IF NOT EXISTS revoked_tokens (
    jti          VARCHAR(256) PRIMARY KEY,
    user_id      BIGINT,
    revoked_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);
