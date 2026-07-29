-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260216-insert-guest-client
-- Insert a default Guest client for unauthenticated announcements

-- 1. Insert Guest Person
INSERT INTO persons (id, first_name, last_name, email, phone, password, national_id, photo_card, total_deliveries)
VALUES (
    '00000000-0000-0000-0000-000000000001', -- Fixed ID for Guest Person
    'Guest', 
    'User', 
    'guest@tiibntick.com', 
    '+000000000000', 
    '$2a$10$NotARealPasswordHashForGuestUser123', -- Dummy BCrypt hash
    'GUEST_NATIONAL_ID', 
    'GUEST_PHOTO_CARD_URL',
    0
)
ON CONFLICT (id) DO NOTHING; -- Assuming id is unique constraint

-- 2. Insert Guest Client linked to Guest Person
INSERT INTO clients (id, person_id, loyalty_status)
VALUES (
    '00000000-0000-0000-0000-000000000000', -- Fixed ID for Guest Client (used in frontend)
    '00000000-0000-0000-0000-000000000001', -- Link to Guest Person
    'BRONZE'
)
ON CONFLICT (id) DO NOTHING;
