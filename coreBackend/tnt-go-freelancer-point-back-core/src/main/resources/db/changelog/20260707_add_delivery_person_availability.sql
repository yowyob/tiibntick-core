-- Create delivery person availability table (similar to opening_hours for Point Relais)
CREATE TABLE delivery_person_availability (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    delivery_person_id UUID NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    start_time TIME,
    end_time TIME,
    is_day_off BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (delivery_person_id) REFERENCES delivery_persons(id) ON DELETE CASCADE
);
