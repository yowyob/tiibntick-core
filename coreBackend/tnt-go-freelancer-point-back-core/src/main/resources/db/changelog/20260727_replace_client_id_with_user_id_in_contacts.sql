-- Rename client_id to user_id in contacts table

ALTER TABLE contacts RENAME COLUMN client_id TO user_id;
