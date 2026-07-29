CREATE TABLE persons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    last_name VARCHAR NOT NULL,
    first_name VARCHAR NOT NULL,
    phone VARCHAR NOT NULL,
    email VARCHAR NOT NULL UNIQUE,
    password VARCHAR NOT NULL,
    national_id VARCHAR NOT NULL UNIQUE,
    photo_card VARCHAR NOT NULL,
    criminal_record VARCHAR,
    rating DOUBLE PRECISION,
    total_deliveries INTEGER DEFAULT 0
);