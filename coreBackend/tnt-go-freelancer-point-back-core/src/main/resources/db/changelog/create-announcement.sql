CREATE TABLE announcements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    duration INTEGER,
    distance DOUBLE PRECISION,
    assigned_freelancer_id UUID,
    destination_relay_point_id UUID,
    logistics_price DOUBLE PRECISION,
    required_vehicle_type VARCHAR,
    signature_url VARCHAR,
    payment_method VARCHAR,
    transport_method VARCHAR
);