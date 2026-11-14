-- A Go delivery need is published as an open QUOTE_REQUEST announcement.
-- delivery_need_id : the delivery need this announcement publishes (NULL for announcements created
--                    directly). The link lives in the gofp local announcement mirror (the BFF is
--                    stateless). At most one announcement per need. Additive and idempotent.
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS delivery_need_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_announcements_delivery_need_id
    ON announcements (delivery_need_id) WHERE delivery_need_id IS NOT NULL;
