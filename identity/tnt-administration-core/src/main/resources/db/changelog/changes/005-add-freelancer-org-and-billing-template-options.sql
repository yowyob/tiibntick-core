--liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-administration-core — FreelancerOrg and Billing Templates Platform Options ()

--changeset tnt-administration-core:005 labels:administration,freelancer-org,billing-templates
-- comment: Adds FreelancerOrg mode and billing templates feature flags to administration.tnt_platform_options table.
-- These options control whether FreelancerOrganization mode and billing policy templates
-- are enabled for a tenant.

ALTER TABLE administration.tnt_platform_options
    ADD COLUMN IF NOT EXISTS freelancer_org_mode_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS max_freelancer_org_fleet_size INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS billing_templates_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS max_billing_template_dsl_level VARCHAR(20) NOT NULL DEFAULT 'SIMPLIFIED';

COMMENT ON COLUMN administration.tnt_platform_options.freelancer_org_mode_enabled
    IS 'When true, freelancers can create a FreelancerOrganization with a fleet and billing policy.';

COMMENT ON COLUMN administration.tnt_platform_options.max_freelancer_org_fleet_size
    IS 'Maximum number of vehicles allowed per FreelancerOrg fleet. Default: 3.';

COMMENT ON COLUMN administration.tnt_platform_options.billing_templates_enabled
    IS 'When true, actors can create billing policies from tnt-billing-templates catalog.';

COMMENT ON COLUMN administration.tnt_platform_options.max_billing_template_dsl_level
    IS 'Maximum DSL access level for non-admin actors: FULL | SIMPLIFIED | NONE.';

-- rollback ALTER TABLE administration.tnt_platform_options DROP COLUMN IF EXISTS freelancer_org_mode_enabled, DROP COLUMN IF EXISTS max_freelancer_org_fleet_size, DROP COLUMN IF EXISTS billing_templates_enabled, DROP COLUMN IF EXISTS max_billing_template_dsl_level;
