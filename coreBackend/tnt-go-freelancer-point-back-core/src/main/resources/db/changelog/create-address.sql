CREATE TABLE addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    street VARCHAR NOT NULL,
    city VARCHAR NOT NULL,
    district VARCHAR NOT NULL,
    type VARCHAR NOT NULL DEFAULT 'PRIMARY',
    country VARCHAR NOT NULL,
    description VARCHAR
);