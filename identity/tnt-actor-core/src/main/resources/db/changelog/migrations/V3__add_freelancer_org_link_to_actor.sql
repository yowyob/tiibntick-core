-- liquibase formatted sql
-- Author: MANFOUO Braun
-- changeset manfouo-braun:tnt-actor-003 labels:tnt-actor-core
-- comment:  — Add FreelancerOrganization link columns to freelancer_profiles.
--          Implements the actor-side of the FreelancerOrganization integration
--          (tnt-organization-core ↔ tnt-actor-core).
--
-- Integration principle: tnt-actor-core does NOT inherit from any tnt-organization-core
-- class. It holds the freelancerOrgId UUID as a logical reference key (no physical FK
-- across module schemas), following the same pattern as actorId referencing the Kernel.

-- ============================================================
-- TABLE: tnt_actor.freelancer_profiles
-- Add FreelancerOrganization link fields
-- ============================================================

ALTER TABLE tnt_actor.freelancer_profiles
    -- UUID of the linked FreelancerOrganization (tnt_freelancer_organization.id).
    -- Nullable: standalone freelancers have no org link.
    -- Logical reference only — no physical FK across module schemas.
    ADD COLUMN IF NOT EXISTS freelancer_org_id  UUID,

    -- Role within the FreelancerOrganization.
    -- Values: 'OWNER' (org creator), 'SUB_DELIVERER' (invited associate).
    -- Null when freelancer_org_id is null.
    ADD COLUMN IF NOT EXISTS role_in_org        VARCHAR(30),

    -- Cached verification status of the linked FreelancerOrganization.
    -- Kept in sync by FreelancerOrgEventConsumer (tnt.freelancer_org.verified event).
    -- Default false — updated reactively when the org is verified.
    ADD COLUMN IF NOT EXISTS is_org_verified    BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for finding all members (OWNER + subs) of a FreelancerOrganization.
-- Supports IFindFreelancerByOrgUseCase.findSubDeliverersByOrg() and findOwnerByOrg().
CREATE INDEX IF NOT EXISTS idx_freelancer_org_id
    ON tnt_actor.freelancer_profiles (freelancer_org_id)
    WHERE freelancer_org_id IS NOT NULL;

-- Composite index for OWNER lookup (findOwnerByOrg — always OWNER role, unique per org).
CREATE UNIQUE INDEX IF NOT EXISTS uix_freelancer_org_owner
    ON tnt_actor.freelancer_profiles (freelancer_org_id)
    WHERE role_in_org = 'OWNER' AND freelancer_org_id IS NOT NULL;

-- Index for org-specific active sub-deliverer queries
-- (e.g., find available sub-deliverers for mission delegation).
CREATE INDEX IF NOT EXISTS idx_freelancer_org_role_status
    ON tnt_actor.freelancer_profiles (freelancer_org_id, role_in_org, actor_status)
    WHERE freelancer_org_id IS NOT NULL;
