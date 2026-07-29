CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    person_id UUID NOT NULL,
    notification_type VARCHAR NOT NULL,
    title VARCHAR NOT NULL,
    message VARCHAR NOT NULL,
    notification_status VARCHAR NOT NULL,
    CONSTRAINT fk_notification_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);
