CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subscription_type VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    price REAL NOT NULL,
    payment_method VARCHAR NOT NULL
);
