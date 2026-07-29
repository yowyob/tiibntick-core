CREATE TABLE person_addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    person_id UUID NOT NULL,
    address_id UUID NOT NULL,
    CONSTRAINT fk_pa_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_pa_address FOREIGN KEY (address_id) REFERENCES addresses(id) ON DELETE CASCADE
);
