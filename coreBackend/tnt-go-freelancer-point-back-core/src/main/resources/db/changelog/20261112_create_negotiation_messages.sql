-- Messages exchanged between a client and the delivery persons who answered their announcement,
-- while they negotiate. Replaces the browser-side Prisma store of the Next BFF: the Core is the only
-- place where the conversation lives, so both sides see the same thread.
-- announcement_id : announcement of tnt-delivery-core (no FK: announcements live in another module's tables)
-- thread_user_id  : Kernel user id of the delivery person this conversation is with. A thread is one
--                   (announcement, delivery person) pair: delivery persons never see each other's offers.
-- sender_user_id  : Kernel user id of the author (= actor id), never taken from the request body
-- read_at         : when the other party saw the message (NULL = unread); drives read receipts and unread badges
-- sender_role     : CLIENT | FREELANCER
CREATE TABLE IF NOT EXISTS gofp_negotiation_messages (
    id              UUID PRIMARY KEY,
    announcement_id UUID         NOT NULL,
    thread_user_id  UUID         NOT NULL,
    sender_user_id  UUID         NOT NULL,
    sender_role     VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMP,
    CONSTRAINT chk_gofp_negotiation_role CHECK (sender_role IN ('CLIENT', 'FREELANCER')),
    CONSTRAINT chk_gofp_negotiation_content CHECK (length(btrim(content)) > 0)
);
CREATE INDEX IF NOT EXISTS idx_gofp_negotiation_announcement
    ON gofp_negotiation_messages (announcement_id, thread_user_id, created_at);
