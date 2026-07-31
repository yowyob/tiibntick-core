-- Align gofp entity mappings with the live schema (missing columns / tables).
-- @author MANFOUO BRAUN

-- gofp_users: identity docs used by GofpUser entity
ALTER TABLE gofp_users ADD COLUMN IF NOT EXISTS cni_number VARCHAR(50);
ALTER TABLE gofp_users ADD COLUMN IF NOT EXISTS nui VARCHAR(50);

-- gofp_relay_points: storage dimensions used by capacity queries
ALTER TABLE gofp_relay_points ADD COLUMN IF NOT EXISTS storage_length DOUBLE PRECISION;
ALTER TABLE gofp_relay_points ADD COLUMN IF NOT EXISTS storage_width DOUBLE PRECISION;
ALTER TABLE gofp_relay_points ADD COLUMN IF NOT EXISTS storage_height DOUBLE PRECISION;
ALTER TABLE gofp_relay_points ADD COLUMN IF NOT EXISTS storage_dimension_unit VARCHAR(10);

-- addresses: optional columns used by AddressEntity (district/description already exist)
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS region VARCHAR;
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS postal_code VARCHAR;
ALTER TABLE addresses ALTER COLUMN district DROP NOT NULL;
ALTER TABLE addresses ALTER COLUMN district SET DEFAULT '';

-- relay_deposits: currency used by RelayDeposit entity
ALTER TABLE relay_deposits ADD COLUMN IF NOT EXISTS currency VARCHAR(10);

-- password reset tokens used by PasswordToken / PasswordSetupController
CREATE TABLE IF NOT EXISTS password_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    person_id   UUID NOT NULL,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_password_tokens_token ON password_tokens (token);
CREATE INDEX IF NOT EXISTS idx_password_tokens_person ON password_tokens (person_id);
