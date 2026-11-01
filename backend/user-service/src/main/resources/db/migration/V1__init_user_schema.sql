-- Flyway Migration V1: Initial User Schema
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Seed initial admin and standard user
INSERT INTO users (id, email, password, role, name, active)
VALUES (1, 'admin@example.com', 'adminpassword', 'ADMIN', 'Admin User', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, password, role, name, active)
VALUES (2, 'user@example.com', 'password123', 'USER', 'Standard User', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Synchronize sequence
SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users));
