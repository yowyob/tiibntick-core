-- Creates the deliveries table.
-- FK to delivery_needs is added later by changeset 053 (after delivery_needs exists).
CREATE TABLE IF NOT EXISTS deliveries (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    delivery_need_id UUID,
    tarif            INTEGER,
    note_livreur     REAL,
    pickup_min_time  TIMESTAMP NOT NULL,
    pickup_max_time  TIMESTAMP NOT NULL,
    delivery_min_time TIMESTAMP NOT NULL,
    delivery_max_time TIMESTAMP NOT NULL,
    delivery_note    DOUBLE PRECISION
);
