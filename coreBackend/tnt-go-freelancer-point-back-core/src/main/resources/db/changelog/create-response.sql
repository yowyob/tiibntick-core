CREATE TABLE responses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    announcement_id UUID NOT NULL,
    delivery_person_id UUID NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_response_announcement FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
    CONSTRAINT fk_response_delivery_person FOREIGN KEY (delivery_person_id) REFERENCES delivery_persons(id) ON DELETE CASCADE
);