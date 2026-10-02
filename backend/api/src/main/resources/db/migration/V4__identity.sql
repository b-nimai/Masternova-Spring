-- Identity (Phase 3.2) — docs/lld/identity.md §3, §9. Only HASHES of tokens are stored.
CREATE TABLE app_user (
    id                UUID         PRIMARY KEY,
    email             VARCHAR(320) NOT NULL UNIQUE,   -- normalised (lower-case) by the Email value object
    display_name      VARCHAR(100) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,          -- "{bcrypt}$2a$10$…" — algorithm id + hash
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL
);

CREATE TABLE app_user_role (
    user_id UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT app_user_role_ck CHECK (role IN ('LEARNER', 'INSTRUCTOR', 'ADMIN'))
);

-- One row per login on a device = one refresh-token FAMILY.
CREATE TABLE auth_session (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ  NOT NULL,
    last_used_at  TIMESTAMPTZ  NOT NULL,
    user_agent    VARCHAR(300),
    revoked_at    TIMESTAMPTZ,
    revoke_reason VARCHAR(30),
    version       BIGINT       NOT NULL
);
CREATE INDEX auth_session_user_idx ON auth_session (user_id);

CREATE TABLE refresh_token (
    id          UUID        PRIMARY KEY,
    session_id  UUID        NOT NULL REFERENCES auth_session (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,          -- SHA-256 hex of the opaque token (always 64 chars)
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ                           -- set exactly once (conditional UPDATE)
);
CREATE INDEX refresh_token_session_idx ON refresh_token (session_id);

CREATE TABLE verification_token (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose    VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);
CREATE INDEX verification_token_user_idx ON verification_token (user_id);
