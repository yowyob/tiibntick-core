CREATE TABLE IF NOT EXISTS announcement_subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    announcement_id UUID NOT NULL,
    delivery_person_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_announcement FOREIGN KEY (announcement_id) REFERENCES announcements(id),
    CONSTRAINT fk_delivery_person FOREIGN KEY (delivery_person_id) REFERENCES delivery_persons(id)
);
