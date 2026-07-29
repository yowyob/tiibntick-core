CREATE TABLE logistics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    delivery_person_id UUID NOT NULL,
    plate_number VARCHAR NOT NULL,
    logistics_type VARCHAR NOT NULL,
    logistics_class VARCHAR NOT NULL,
    logistic_image VARCHAR NOT NULL,
    rating DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    tank_capacity DOUBLE PRECISION,
    luggage_max_capacity DOUBLE PRECISION,
    total_seat_number INTEGER,
    color VARCHAR,
    CONSTRAINT fk_logistics_delivery_person FOREIGN KEY (delivery_person_id) REFERENCES delivery_persons(id) ON DELETE CASCADE
);