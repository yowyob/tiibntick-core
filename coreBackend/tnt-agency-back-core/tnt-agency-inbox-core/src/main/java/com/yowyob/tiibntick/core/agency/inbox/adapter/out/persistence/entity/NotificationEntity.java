package com.yowyob.tiibntick.core.agency.inbox.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_inbox", name = "notifications")
public class NotificationEntity {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agency_id")
    private UUID agencyId;

    private String type;

    @Column("event_type")
    private String eventType;

    private String title;
    private String body;
    private String href;

    @Column("is_read")
    private boolean read;

    @Column("created_at")
    private Instant createdAt;
}
