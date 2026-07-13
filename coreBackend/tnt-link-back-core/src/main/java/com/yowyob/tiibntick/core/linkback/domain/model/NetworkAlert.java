package com.yowyob.tiibntick.core.linkback.domain.model;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.domain.exception.NetworkAlertDomainException;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a community-reported traffic alert on the Link network
 * (pothole, flooding, road closure...). Genuinely Link-specific — no equivalent
 * exists in tnt-incident-core, which manages incidents blocking an active
 * delivery, not community-reported road hazards.
 *
 * @author Dilane PAFE
 */
@Getter
@Builder
public class NetworkAlert {

    private final UUID id;
    private final UUID tenantId;
    private final UUID reporterId;
    private final AlertType type;
    private final String description;
    private final GeoPoint location;
    private final AlertSeverity severity;
    private AlertStatus status;
    private int confirmCount;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    public static NetworkAlert report(UUID tenantId, UUID reporterId, AlertType type,
                                       String description, GeoPoint location, AlertSeverity severity) {
        if (location == null) {
            throw new NetworkAlertDomainException("A network alert must have a location");
        }
        Instant now = Instant.now();
        return NetworkAlert.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .reporterId(reporterId)
                .type(type)
                .description(description)
                .location(location)
                .severity(severity != null ? severity : AlertSeverity.MEDIUM)
                .status(AlertStatus.ACTIVE)
                .confirmCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void confirm() {
        if (status != AlertStatus.ACTIVE) {
            throw new NetworkAlertDomainException("Cannot confirm a non-active alert: " + id);
        }
        this.confirmCount++;
        this.updatedAt = Instant.now();
    }

    public void resolve() {
        if (status == AlertStatus.RESOLVED) {
            throw new NetworkAlertDomainException("Alert already resolved: " + id);
        }
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.updatedAt = this.resolvedAt;
    }
}
