-- =============================================================================
-- tnt-geo-core: relay_hubs table
-- Physical parcel relay hubs linked to road_nodes
-- Author: MANFOUO Braun
-- =============================================================================

CREATE TABLE IF NOT EXISTS tnt_geography.relay_hubs (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    branch_id         UUID         NOT NULL,
    node_id           VARCHAR(36)  NOT NULL,
    capacity_slots    INTEGER      NOT NULL,
    current_occupancy INTEGER      NOT NULL DEFAULT 0,
    operator_actor_id VARCHAR(36),
    status            VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT relay_hubs_pk           PRIMARY KEY (id),
    CONSTRAINT relay_hubs_node_fk      FOREIGN KEY (node_id) REFERENCES tnt_geography.road_nodes(id),
    CONSTRAINT relay_hubs_capacity_pos CHECK (capacity_slots > 0),
    CONSTRAINT relay_hubs_occupancy_nn CHECK (current_occupancy >= 0),
    CONSTRAINT relay_hubs_occupancy_le CHECK (current_occupancy <= capacity_slots),
    CONSTRAINT relay_hubs_status_check CHECK (status IN ('ACTIVE','FULL','TEMPORARILY_CLOSED','PERMANENTLY_CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_relay_hubs_tenant
    ON tnt_geography.relay_hubs (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_relay_hubs_branch
    ON tnt_geography.relay_hubs (branch_id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_relay_hubs_node
    ON tnt_geography.relay_hubs (node_id);
