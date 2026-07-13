package com.yowyob.tiibntick.core.agency.inbox.domain;

import java.time.Instant;
import java.util.UUID;

/** Ported from tnt-agency {@code AgencyNotification}. */
public class AgencyNotification {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final String type;
    private final String eventType;
    private final String title;
    private final String body;
    private final String href;
    private boolean read;
    private final Instant createdAt;

    public AgencyNotification(UUID id, UUID tenantId, UUID agencyId,
                              String type, String eventType,
                              String title, String body, String href,
                              boolean read, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.type = type;
        this.eventType = eventType;
        this.title = title;
        this.body = body;
        this.href = href != null ? href : "/";
        this.read = read;
        this.createdAt = createdAt;
    }

    public static AgencyNotification unread(UUID id, UUID tenantId, UUID agencyId,
                                          String type, String eventType,
                                          String title, String body, String href,
                                          Instant createdAt) {
        return new AgencyNotification(id, tenantId, agencyId, type, eventType,
                title, body, href, false, createdAt);
    }

    public static String mapUiType(String eventType) {
        return switch (eventType) {
            case "HUB_ALERT", "PARCEL_EXPIRED" -> "warning";
            case "DELIVERY_CONFIRMED", "BRANCH_CREATED" -> "success";
            case "MISSION_ASSIGNED", "MISSION_STARTED" -> "info";
            default -> "info";
        };
    }

    public void markRead() {
        this.read = true;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public String getType() { return type; }
    public String getEventType() { return eventType; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getHref() { return href; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
}
