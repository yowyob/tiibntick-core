-- liquibase formatted sql
-- Author: MANFOUO Braun
-- changeset manfouo-braun:tnt-actor-004 labels:tnt-actor-core
-- comment: Add blockchain DID column to all actor profile tables. Populated by
--          tnt-trust-core via IssueDIDUseCase.issue(...) when KYC is validated to
--          VERIFIED status (see ActorKycService.validateKyc()). Nullable — actors
--          without a completed KYC verification have no DID yet.

ALTER TABLE tnt_actor.deliverer_profiles
    ADD COLUMN IF NOT EXISTS blockchain_did VARCHAR(150);

ALTER TABLE tnt_actor.freelancer_profiles
    ADD COLUMN IF NOT EXISTS blockchain_did VARCHAR(150);

ALTER TABLE tnt_actor.relay_operator_profiles
    ADD COLUMN IF NOT EXISTS blockchain_did VARCHAR(150);

ALTER TABLE tnt_actor.client_profiles
    ADD COLUMN IF NOT EXISTS blockchain_did VARCHAR(150);
