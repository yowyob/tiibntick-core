CREATE TABLE opening_hours (
    id UUID PRIMARY KEY,
    logistics_id UUID NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    open_time TIME,
    close_time TIME,
    is_closed BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (logistics_id) REFERENCES logistics(id) ON DELETE CASCADE
);

ALTER TABLE logistics ADD COLUMN storefront_photo VARCHAR(255);
