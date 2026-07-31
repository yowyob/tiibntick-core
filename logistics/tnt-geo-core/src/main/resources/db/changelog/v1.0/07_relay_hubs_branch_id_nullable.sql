--liquibase formatted sql
--changeset MANFOUO_Braun:007_relay_hubs_branch_id_nullable
--comment: Allows tnt_geography.relay_hubs.branch_id to be NULL for freelance/independent hubs (e.g. GOFP relay points), which by construction have no agency branch. Agency-managed hubs keep a non-null branch_id.

ALTER TABLE tnt_geography.relay_hubs ALTER COLUMN branch_id DROP NOT NULL;
