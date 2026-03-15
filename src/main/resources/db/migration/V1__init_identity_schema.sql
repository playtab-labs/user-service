-- =========================================
-- 0) Extensions
-- =========================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

-- =========================================
-- 1) identities
-- =========================================
CREATE TABLE auth_identities (
                                 identity_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 ci_hash     VARCHAR(256),
                                 role        VARCHAR(20) NOT NULL DEFAULT 'USER',
                                 status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                 created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                 CONSTRAINT uq_identities_ci_hash UNIQUE(ci_hash),
                                 CONSTRAINT ck_identities_role CHECK (role IN ('USER','ADMIN')),
                                 CONSTRAINT ck_identities_status CHECK (status IN ('ACTIVE','LOCKED','DELETED'))
);

-- =========================================
-- 2) profiles (1:1)
-- =========================================
CREATE TABLE user_profiles (
                               profile_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               identity_id   UUID NOT NULL,
                               email         CITEXT NOT NULL,
                               name          VARCHAR(100),
                               gender        VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED',
                               phone_number  VARCHAR(30),
                               birth_date    DATE,
                               is_adult      BOOLEAN DEFAULT FALSE,
                               nationality   VARCHAR(10) DEFAULT 'KR',

                               created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                               CONSTRAINT fk_profiles_identity
                                   FOREIGN KEY (identity_id) REFERENCES auth_identities(identity_id) ON DELETE CASCADE,

                               CONSTRAINT uq_profiles_identity_id UNIQUE(identity_id),

                               CONSTRAINT uq_profiles_email UNIQUE(email),

                               CONSTRAINT ck_profiles_gender CHECK (gender IN ('UNSPECIFIED','MALE','FEMALE','OTHER'))
);

-- =========================================
-- 2-1) settings (1:1)
-- =========================================
CREATE TABLE user_settings (
                               settings_id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               identity_id                 UUID NOT NULL,

                               locale                      VARCHAR(20) NOT NULL DEFAULT 'ko-KR',
                               push_enabled                BOOLEAN NOT NULL DEFAULT TRUE,
                               email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,

                               created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                               CONSTRAINT fk_settings_identity
                                   FOREIGN KEY (identity_id) REFERENCES auth_identities(identity_id) ON DELETE CASCADE,

                               CONSTRAINT uq_settings_identity_id UNIQUE(identity_id)
);

-- =========================================
-- 3) credentials
-- =========================================
CREATE TABLE auth_credentials (
                                  credential_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  identity_id    UUID NOT NULL,

                                  type           VARCHAR(20) NOT NULL,
                                  identifier     VARCHAR(255) NOT NULL,
                                  password_hash  VARCHAR(512),
                                  external_meta  JSONB,
                                  is_primary     BOOLEAN NOT NULL DEFAULT FALSE,
                                  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                  CONSTRAINT fk_credentials_identity
                                      FOREIGN KEY (identity_id) REFERENCES auth_identities(identity_id) ON DELETE CASCADE,

                                  CONSTRAINT uq_credentials_type_identifier UNIQUE(type, identifier),

                                  CONSTRAINT ck_credentials_type CHECK (type IN ('EMAIL','GOOGLE','KAKAO','NAVER','APPLE'))
);

-- =========================================
-- 4) sessions
-- =========================================
CREATE TABLE auth_sessions (
                               session_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               identity_id         UUID NOT NULL,

                               refresh_token_hash  VARCHAR(256) NOT NULL,
                               device_fingerprint  VARCHAR(256),
                               user_agent          TEXT,
                               ip_address          INET,
                               expires_at          TIMESTAMPTZ NOT NULL,
                               is_revoked          BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                               CONSTRAINT fk_sessions_identity
                                   FOREIGN KEY (identity_id) REFERENCES auth_identities(identity_id) ON DELETE CASCADE
);

-- =========================================
-- 5) consents
-- =========================================
CREATE TABLE auth_consents (
                               consent_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               identity_id    UUID NOT NULL,
                               terms_version  VARCHAR(50) NOT NULL,
                               type           VARCHAR(20) NOT NULL,
                               is_agreed      BOOLEAN NOT NULL,
                               agreed_at      TIMESTAMPTZ,

                               CONSTRAINT fk_consents_identity
                                   FOREIGN KEY (identity_id) REFERENCES auth_identities(identity_id) ON DELETE CASCADE,

                               CONSTRAINT uq_consents_identity_type_terms UNIQUE(identity_id, type, terms_version),

                               CONSTRAINT ck_consents_type CHECK (type IN ('PRIVACY','SERVICE','MARKETING'))
);

-- =========================================
-- Indexes
-- =========================================
CREATE INDEX idx_profiles_email ON user_profiles(email);
CREATE INDEX idx_settings_identity_id ON user_settings(identity_id);
CREATE INDEX idx_credentials_identifier ON auth_credentials(identifier);
CREATE INDEX idx_sessions_refresh_hash ON auth_sessions(refresh_token_hash);

CREATE INDEX idx_consents_identity_type ON auth_consents(identity_id, type);
CREATE INDEX idx_consents_identity_type_terms ON auth_consents(identity_id, type, terms_version);
