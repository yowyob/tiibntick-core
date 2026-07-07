-- liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-organization-core (L2 Identity) — 
-- Description: Adds access_difficulty and zone_type columns to tnt_branch
--              to support the updated ServiceZone value object.
--              Both columns are nullable with safe defaults for backward compatibility.

-- changeset manfouo-braun:v0007-alter-branch-add-zone-type dbms:postgresql splitStatements:true endDelimiter:;
ALTER TABLE tnt_branch
    ADD COLUMN IF NOT EXISTS service_zone_access_difficulty VARCHAR(20) DEFAULT 'LOW',
    ADD COLUMN IF NOT EXISTS service_zone_type              VARCHAR(20) DEFAULT 'URBAN';

-- Back-fill existing rows with safe defaults
-- changeset manfouo-braun:v0007-backfill-branch-zone-columns dbms:postgresql
UPDATE tnt_branch
SET service_zone_access_difficulty = 'LOW',
    service_zone_type              = 'URBAN'
WHERE service_zone_name IS NOT NULL
  AND service_zone_access_difficulty IS NULL;
