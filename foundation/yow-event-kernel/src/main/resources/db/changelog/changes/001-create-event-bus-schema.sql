--liquibase formatted sql
--changeset MANFOUO_Braun:001-create-event-bus-schema
--comment: Create the dedicated event_bus schema for yow-event-kernel (Outbox + Kafka + Avro + DLQ + Replay)

CREATE SCHEMA IF NOT EXISTS event_bus;

--rollback DROP SCHEMA IF EXISTS event_bus CASCADE;
