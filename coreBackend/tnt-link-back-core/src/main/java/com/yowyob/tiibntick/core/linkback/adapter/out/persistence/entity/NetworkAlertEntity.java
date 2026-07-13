package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity mapped to tnt_link.network_alerts.
 *
 * @author Dilane PAFE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_link", value = "network_alerts")
public class NetworkAlertEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("reporter_id")
    private UUID reporterId;

    @Column("alert_type")
    private String alertType;

    @Column("description")
    private String description;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("severity")
    private String severity;

    @Column("status")
    private String status;

    @Column("confirm_count")
    private int confirmCount;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("resolved_at")
    private Instant resolvedAt;

    @Version
    private long version;
}
