-- What a delivery person accepts to carry, and when: storage for the "Politiques" screen that used to be
-- kept in the browser only. Consumed by candidate matching (hard constraints) and shown to clients.
-- availability      : JSON array of {day: MONDAY..SUNDAY, start: "HH:mm", end: "HH:mm", active: boolean}.
--                     Empty/NULL = no constraint.
-- accepted_packages : JSON array of PackageType codes (STANDARD, DOCUMENT, FRAGILE, PERISHABLE, OVERSIZED…).
--                     Empty/NULL = everything is accepted.
-- min/max_weight_kg : NULL = unbounded on that side.
CREATE TABLE IF NOT EXISTS freelancer_eligibility (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    delivery_person_id UUID NOT NULL UNIQUE,
    availability       TEXT,
    accepted_packages  TEXT,
    min_weight_kg      DOUBLE PRECISION,
    max_weight_kg      DOUBLE PRECISION,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_freelancer_eligibility_delivery_person
        FOREIGN KEY (delivery_person_id) REFERENCES delivery_persons(id) ON DELETE CASCADE,
    CONSTRAINT chk_freelancer_eligibility_weight
        CHECK (min_weight_kg IS NULL OR max_weight_kg IS NULL OR min_weight_kg <= max_weight_kg)
);
