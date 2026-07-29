CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    person_id UUID NOT NULL,
    person_receiver_id UUID NOT NULL,
    message VARCHAR NOT NULL,
    num_of_like INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_person_author FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_person_receiver FOREIGN KEY (person_receiver_id) REFERENCES persons(id) ON DELETE CASCADE
);
