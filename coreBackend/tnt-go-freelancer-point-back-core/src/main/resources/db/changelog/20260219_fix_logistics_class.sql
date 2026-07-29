--liquibase formatted sql
--changeset TiiBnTickTeam:041-fix-logistics-class-economy

-- Fix ECONOMY -> STANDARD for the bicycle logistics entry that was seeded with an invalid enum value
UPDATE logistics SET logistics_class = 'STANDARD' WHERE logistics_class = 'ECONOMY';
