-- Remove duplicate subscription records, keeping only the earliest one per (announcement_id, delivery_person_id)
DELETE FROM announcement_subscriptions
WHERE id NOT IN (
    SELECT DISTINCT ON (announcement_id, delivery_person_id) id
    FROM announcement_subscriptions
    ORDER BY announcement_id, delivery_person_id, created_at ASC
);

-- Prevent future duplicates
ALTER TABLE announcement_subscriptions
    ADD CONSTRAINT uq_announcement_delivery_person UNIQUE (announcement_id, delivery_person_id);
