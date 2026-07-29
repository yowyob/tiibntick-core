-- Creates the packets table referenced by announcements
CREATE TABLE IF NOT EXISTS packets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    announcement_id UUID,
    title VARCHAR(255),
    description TEXT,
    weight DOUBLE PRECISION,
    height DOUBLE PRECISION,
    width DOUBLE PRECISION,
    length DOUBLE PRECISION,
    is_fragile BOOLEAN DEFAULT FALSE,
    is_perishable BOOLEAN DEFAULT FALSE,
    photo_url VARCHAR(512),
    CONSTRAINT fk_packet_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
);
