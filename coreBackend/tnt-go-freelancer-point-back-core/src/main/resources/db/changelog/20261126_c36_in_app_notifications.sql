-- Lot C-36. Additive and idempotent: the gofp "notifications" table becomes the in-app inbox of the
-- three roles (sender, courier, relay owner). person_id now holds the recipient's user id (the JWT
-- userId), so the legacy foreign key to "persons" (a table no current flow fills) is dropped:
-- maintainer decision recorded here, no data is removed.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS fk_notification_person;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS event_key VARCHAR(200);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS link_kind VARCHAR(30);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS link_id UUID;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS params TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_event_key ON notifications(event_key) WHERE event_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_notifications_person_created ON notifications(person_id, created_at DESC);
