--liquibase formatted sql
--changeset jeff-belekotan:002_create_onboarding_applications
--comment: Ported from tnt-agency V010 + V013 kernel columns

CREATE TABLE IF NOT EXISTS agency_onboarding.onboarding_applications (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    agency_id                   UUID NOT NULL UNIQUE REFERENCES agency_org.agencies(id) ON DELETE CASCADE,
    applicant_user_id           UUID NOT NULL,
    legal_name                  VARCHAR(255),
    owner_name                  VARCHAR(255) NOT NULL,
    owner_email                 VARCHAR(255) NOT NULL,
    owner_phone                 VARCHAR(30) NOT NULL,
    owner_national_id           VARCHAR(100),
    owner_id_type               VARCHAR(30) DEFAULT 'CNI',
    doc_cni_key                 VARCHAR(512),
    doc_rccm_key                VARCHAR(512),
    doc_proof_key               VARCHAR(512),
    application_status          VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    rejection_reason            TEXT,
    reviewed_by                 UUID,
    reviewed_at                 TIMESTAMPTZ,
    kernel_business_actor_id    UUID,
    kernel_identity_completed_at TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_onboarding_status ON agency_onboarding.onboarding_applications (application_status);
CREATE INDEX IF NOT EXISTS idx_onboarding_applicant ON agency_onboarding.onboarding_applications (applicant_user_id);
CREATE INDEX IF NOT EXISTS idx_onboarding_owner_email ON agency_onboarding.onboarding_applications (owner_email);
CREATE INDEX IF NOT EXISTS idx_onboarding_kernel_actor ON agency_onboarding.onboarding_applications (kernel_business_actor_id);

ALTER TABLE agency_onboarding.onboarding_applications ENABLE ROW LEVEL SECURITY;

--rollback DROP TABLE IF EXISTS agency_onboarding.onboarding_applications CASCADE;
