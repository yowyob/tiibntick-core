package com.yowyob.tiibntick.core.agency.sync.adapter.out.persistence.entity;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_sync", name = "device_registrations")
public class DeviceRegistrationEntity implements Persistable<UUID>, TntPersistableEntity {

    @Transient private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agency_id")
    private UUID agencyId;

    @Column("user_id")
    private UUID userId;

    @Column("device_id")
    private String deviceId;

    @Column("last_sync_token")
    private String lastSyncToken;

    @Column("registered_at")
    private Instant registeredAt;

    @Column("updated_at")
    private Instant updatedAt;
}
