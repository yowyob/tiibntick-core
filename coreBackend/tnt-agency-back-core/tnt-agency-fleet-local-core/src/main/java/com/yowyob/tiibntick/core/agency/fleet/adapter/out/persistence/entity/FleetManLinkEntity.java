package com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_fleet", name = "fleetman_link")
public class FleetManLinkEntity implements Persistable<UUID> {

    @Id
    @Column("agency_id")
    private UUID agencyId;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("fleetman_user_id")
    private String fleetmanUserId;

    @Column("fleetman_fleet_id")
    private String fleetmanFleetId;

    private String email;

    @Column("refresh_token_enc")
    private String refreshTokenEnc;

    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Transient
    private boolean isNewEntity = true;

    @Override
    public UUID getId() {
        return agencyId;
    }

    @Override
    public boolean isNew() {
        return isNewEntity;
    }

    public void markNotNew() {
        this.isNewEntity = false;
    }
}
