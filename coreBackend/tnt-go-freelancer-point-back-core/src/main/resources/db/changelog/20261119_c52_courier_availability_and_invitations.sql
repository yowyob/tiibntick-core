-- Lot C-52. Additive and idempotent.
-- 1. Availability chosen by the courier: AVAILABLE (default) or PAUSED. A paused courier is left out
--    of candidate matching. "Busy" is not stored: it is computed from unfinished deliveries.
-- 2. Invitations: a client proposes an open delivery need to one courier, who accepts or declines
--    within a deadline. A separate table (not columns on delivery_needs) keeps the history of every
--    invitation of a need (declined, expired, then another courier) and lets a courier list his own.
ALTER TABLE gofp_freelancers ADD COLUMN IF NOT EXISTS availability_status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE';

CREATE TABLE IF NOT EXISTS gofp_need_invitations (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    delivery_need_id UUID NOT NULL REFERENCES delivery_needs(id) ON DELETE CASCADE,
    freelancer_id    UUID NOT NULL REFERENCES gofp_freelancers(id) ON DELETE CASCADE,
    invited_by       UUID NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    estimated_price  DOUBLE PRECISION,
    expires_at       TIMESTAMP NOT NULL,
    decline_reason   VARCHAR(500),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at     TIMESTAMP,
    CONSTRAINT chk_gofp_need_invitations_status
        CHECK (status IN ('OPEN', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELLED'))
);

-- At most one open invitation per delivery need.
CREATE UNIQUE INDEX IF NOT EXISTS uq_gofp_need_invitations_open
    ON gofp_need_invitations (delivery_need_id) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_gofp_need_invitations_freelancer
    ON gofp_need_invitations (freelancer_id, status);
