-- Kernel wallet migration: the delivery person's temporary receive code.
-- driver_user_id           : JWT subject of the delivery person who generated the code (= owner of the
--                            Kernel wallet that will receive the payment).
-- receive_code             : short-lived Kernel receive code; the client's transfer is addressed to it.
-- receive_code_expires_at  : after this instant the code is useless; the driver regenerates one.
-- Payment state itself (PAID / UNPAID / commission due) lives in billing.wallet_settlements.
ALTER TABLE deliveries
    ADD COLUMN IF NOT EXISTS driver_user_id          UUID,
    ADD COLUMN IF NOT EXISTS receive_code            VARCHAR(64),
    ADD COLUMN IF NOT EXISTS receive_code_expires_at TIMESTAMPTZ;
