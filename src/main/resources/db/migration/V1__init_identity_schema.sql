CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1) identities
CREATE TABLE IF NOT EXISTS auth_identities (
                                               identity_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ci_hash     VARCHAR(256),
    role        VARCHAR(20) NOT NULL DEFAULT 'USER',
    status      VARCHAR(20) DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_identities_ci_hash UNIQUE(ci_hash)
    );

-- 2) profiles (1:1)
CREATE TABLE IF NOT EXISTS user_profiles (
                                             profile_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identity_id   UUID NOT NULL,
    email         VARCHAR(255),
    name          VARCHAR(100),
    nickname      VARCHAR(50),
    phone_number  VARCHAR(20),
    birth_date    DATE,
    is_adult      BOOLEAN DEFAULT FALSE,
    nationality   VARCHAR(10) DEFAULT 'KR',
    updated_at    TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_profiles_identity
    FOREIGN KEY (identity_id) REFERENCES auth_identities(identity_id) ON DELETE CASCADE,
    CONSTRAINT uq_profiles_identity_id UNIQUE(identity_id)
    );

-- 3) credentials
CREATE TABLE IF NOT EXISTS auth_credentials (
                                                credential_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identity_id    UUID NOT NULL,
    type           VARCHAR(20) NOT NULL,
    identifier     VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(512),
    external_meta  JSONB,
    is_primary     BOOLEAN DEFAULT FALSE,
    created_at     TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_credentials_identity
    FOREIGN KEY (identity_id) REFERENCES auth_identities(identity_id) ON DELETE CASCADE,
    CONSTRAINT uq_credentials_type_identifier UNIQUE(type, identifier)
    );

-- 4) sessions
CREATE TABLE IF NOT EXISTS auth_sessions (
                                             session_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identity_id         UUID NOT NULL,
    refresh_token_hash  VARCHAR(256) NOT NULL,
    device_fingerprint  VARCHAR(256),
    user_agent          TEXT,
    ip_address          INET,
    expires_at          TIMESTAMPTZ NOT NULL,
    is_revoked          BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_sessions_identity
    FOREIGN KEY (identity_id) REFERENCES auth_identities(identity_id) ON DELETE CASCADE
    );

-- 5) consents
CREATE TABLE IF NOT EXISTS auth_consents (
    consent_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identity_id    UUID NOT NULL,
    terms_version  VARCHAR(20) NOT NULL,
    type           VARCHAR(20) NOT NULL,
    is_agreed      BOOLEAN NOT NULL,
    agreed_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_consents_identity
        FOREIGN KEY (identity_id) REFERENCES auth_identities(identity_id) ON DELETE CASCADE
    );

-- indexes
CREATE INDEX IF NOT EXISTS idx_profiles_email ON user_profiles(email);
CREATE INDEX IF NOT EXISTS idx_credentials_identifier ON auth_credentials(identifier);
CREATE INDEX IF NOT EXISTS idx_sessions_refresh_hash ON auth_sessions(refresh_token_hash);
