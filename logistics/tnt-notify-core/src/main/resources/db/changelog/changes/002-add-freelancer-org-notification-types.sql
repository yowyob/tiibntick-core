--liquibase formatted sql
--changeset MANFOUO_Braun:002-add-freelancer-org-notify
--comment: Adds FreelancerOrg notification type support to tnt_notifications table ().
-- Adds notification_type column for semantic filtering and analytics.

ALTER TABLE tnt_notifications
    ADD COLUMN IF NOT EXISTS notification_type VARCHAR(80) DEFAULT 'SYSTEM';

COMMENT ON COLUMN tnt_notifications.notification_type IS
    'Semantic notification type for analytics and filtering. '
    'Values: FREELANCER_ORG_VERIFIED | FREELANCER_ORG_SUSPENDED | SUB_DELIVERER_INVITED | '
    'SUB_DELIVERER_MISSION_ASSIGNED | KYC_LEVEL_UPGRADED | BILLING_POLICY_TEMPLATE_APPLIED | SURCHARGE_TRIGGERED | ...';

CREATE INDEX IF NOT EXISTS idx_tnt_notifications_type
    ON tnt_notifications (notification_type);

CREATE INDEX IF NOT EXISTS idx_tnt_notifications_type_dest
    ON tnt_notifications (notification_type, destinataire_id);

--rollback ALTER TABLE tnt_notifications DROP COLUMN IF EXISTS notification_type;
