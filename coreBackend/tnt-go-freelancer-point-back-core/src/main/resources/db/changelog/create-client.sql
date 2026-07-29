CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    person_id UUID NOT NULL,
    loyalty_status VARCHAR NOT NULL,
    CONSTRAINT fk_client_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);