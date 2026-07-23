--liquibase formatted sql
--changeset MANFOUO_Braun:004-create-event-schemas-table
--comment: Create event_bus.event_schemas — Avro schema registry backing EventSchemaEntity / EventSchemaRepository

CREATE TABLE IF NOT EXISTS event_bus.event_schemas
(
    id                     VARCHAR(36)  NOT NULL,
    event_type             VARCHAR(200) NOT NULL,
    version                INTEGER      NOT NULL,
    solution_code          VARCHAR(10)  NOT NULL,
    avro_schema_json       TEXT         NOT NULL,
    compatibility          VARCHAR(30)  NOT NULL DEFAULT 'FULL',
    is_backward_compatible BOOLEAN      NOT NULL DEFAULT TRUE,
    registered_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    deprecated_at          TIMESTAMP,

    CONSTRAINT pk_event_schemas PRIMARY KEY (id),
    CONSTRAINT uq_event_schemas_type_solution_version
        UNIQUE (event_type, solution_code, version)
);

--rollback DROP TABLE IF EXISTS event_bus.event_schemas;
