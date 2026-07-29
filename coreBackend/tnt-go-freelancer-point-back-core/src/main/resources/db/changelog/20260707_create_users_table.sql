-- Create the users table (base entity for all actors)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    last_name VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Link persons to users (Person inherits from User)
ALTER TABLE persons ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE persons ADD CONSTRAINT fk_persons_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- Update delivery_needs: replace client_id with user_id
ALTER TABLE delivery_needs DROP CONSTRAINT IF EXISTS delivery_needs_client_id_fkey;
ALTER TABLE delivery_needs RENAME COLUMN client_id TO user_id;
ALTER TABLE delivery_needs ADD CONSTRAINT fk_delivery_needs_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
