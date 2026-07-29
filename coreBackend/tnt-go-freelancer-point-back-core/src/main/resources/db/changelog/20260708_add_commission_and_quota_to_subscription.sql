-- ============================================================
-- Migration 057: Add commission tracking fields to subscriptions
--                and commission breakdown to payments
-- Author : TiiBnTickTeam
-- Date   : 2026-07-08
-- ============================================================

-- 1. Link subscription to its delivery person (bidirectional lookup)
ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS delivery_person_id UUID REFERENCES delivery_persons(id) ON DELETE SET NULL;

-- 2. Monthly quota tracking
ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS deliveries_used  INTEGER   NOT NULL DEFAULT 0;

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS reset_date       TIMESTAMP;

-- Index to make findByDeliveryPersonId fast
CREATE INDEX IF NOT EXISTS idx_subscriptions_delivery_person_id
    ON subscriptions(delivery_person_id);

-- 3. Commission breakdown on each payment
--    commission_amount : the amount retained by TiiBnTick
--    net_amount        : what the delivery person actually receives
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS commission_amount DOUBLE PRECISION;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS net_amount        DOUBLE PRECISION;

-- ============================================================
-- Backfill: set reset_date to first day of next month for all
-- existing ACTIVE subscriptions so the cron logic kicks in
-- correctly on the first delivery completion after this migration.
-- ============================================================
UPDATE subscriptions
SET reset_date = date_trunc('month', NOW()) + INTERVAL '1 month'
WHERE status = 'ACTIVE'
  AND reset_date IS NULL;
