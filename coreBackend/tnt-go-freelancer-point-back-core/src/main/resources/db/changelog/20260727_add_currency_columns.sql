-- liquibase formatted sql

-- changeset fatanga:20260727-add-currency-columns

ALTER TABLE announcements ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'XAF';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'XAF';
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'XAF';
ALTER TABLE relay_point_subscriptions ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'XAF';
