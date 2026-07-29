--liquibase formatted sql
--changeset TiiBnTickTeam:042-remove-duplicate-admin

-- Remove the SQL-seeded admin@test.com user, keep only the bootstrap admin (bernicetsafack@gmail.com)
DELETE FROM persons WHERE email = 'admin@test.com';
