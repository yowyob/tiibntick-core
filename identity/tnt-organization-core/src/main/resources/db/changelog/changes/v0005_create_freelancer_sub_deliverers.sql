-- liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-organization-core (L2 Identity) — 
-- Description: Creates the tnt_freelancer_sub_deliverer table.
--              Stores sub-deliverer associations for FreelancerOrganizations.
--              A FreelancerOrganization may have at most 5 ACTIVE/PENDING sub-deliverers.

-- changeset manfouo-braun:v0005-create-freelancer-sub-deliverers dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_freelancer_sub_deliverer
(
    -- Synthetic primary key
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- FK to the owning FreelancerOrganization
    freelancer_org_id   UUID            NOT NULL,

    -- Sub-deliverer actor UUID (from tnt-actor-core)
    deliverer_actor_id  UUID            NOT NULL,

    -- Association lifecycle status (AssociationStatus enum name)
    association_status  VARCHAR(30)     NOT NULL DEFAULT 'PENDING_ACCEPTANCE',

    -- Commission fraction paid to the sub-deliverer (0.0000 – 1.0000)
    commission_rate     DECIMAL(6,4),

    -- Timestamp when the association became ACTIVE (nullable until accepted)
    associated_since    TIMESTAMPTZ,

    -- Timestamp when the association was terminated (nullable)
    terminated_at       TIMESTAMPTZ,

    -- Audit
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tnt_freelancer_sub_deliverer PRIMARY KEY (id),

    CONSTRAINT fk_freelancer_sub_deliverer_org
        FOREIGN KEY (freelancer_org_id)
        REFERENCES tnt_freelancer_organization (id)
        ON DELETE CASCADE
);

-- Composite index for the most common query: all sub-deliverers of an org
-- changeset manfouo-braun:v0005-idx-sub-deliverer-org-id dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_freelancer_sub_deliverer_org_id
    ON tnt_freelancer_sub_deliverer (freelancer_org_id);

-- Composite unique index: one row per (org, actor) pair in ACTIVE/PENDING state
-- Note: multiple TERMINATED rows per (org, actor) are allowed (re-association after revocation)
-- changeset manfouo-braun:v0005-idx-sub-deliverer-actor dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_freelancer_sub_deliverer_actor_org
    ON tnt_freelancer_sub_deliverer (freelancer_org_id, deliverer_actor_id, association_status);
