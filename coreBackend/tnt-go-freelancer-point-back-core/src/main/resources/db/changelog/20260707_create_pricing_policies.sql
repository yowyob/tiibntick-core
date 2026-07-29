-- Delivery Person Pricing Policy
CREATE TABLE delivery_person_pricing (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    delivery_person_id UUID NOT NULL UNIQUE,
    price_per_kg DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    price_per_cbm DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    price_per_km DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    fragile_surcharge DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    perishable_surcharge DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    base_fee DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    currency VARCHAR(10) DEFAULT 'FCFA',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (delivery_person_id) REFERENCES delivery_persons(id) ON DELETE CASCADE
);

-- Logistics (Point Relais) Pricing Policy
CREATE TABLE logistics_pricing (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    logistics_id UUID NOT NULL UNIQUE,
    price_per_kg DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    price_per_cbm DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    price_per_day DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    grace_period_days INTEGER NOT NULL DEFAULT 0,
    penalty_per_day DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    fragile_surcharge DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    perishable_surcharge DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    base_fee DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    currency VARCHAR(10) DEFAULT 'FCFA',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (logistics_id) REFERENCES logistics(id) ON DELETE CASCADE
);
