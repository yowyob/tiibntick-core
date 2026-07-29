-- Add OTP confirmation fields to deliveries table.
-- pickup_otp_hash    : BCrypt hash of the 6-digit code given to the shipper.
--                     The delivery person must provide this code to confirm PICKED_UP.
-- delivery_otp_hash  : BCrypt hash of the 6-digit code sent to the recipient.
--                     The delivery person must provide this code to confirm DELIVERED (direct).
-- actual_pickup_time : Timestamp set when PICKED_UP is confirmed via OTP.
-- actual_delivery_time: Timestamp set when DELIVERED is confirmed via OTP.

ALTER TABLE deliveries
    ADD COLUMN IF NOT EXISTS pickup_otp_hash      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS delivery_otp_hash    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS actual_pickup_time   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS actual_delivery_time TIMESTAMPTZ;
