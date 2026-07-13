package com.yowyob.tiibntick.core.linkback.domain.model;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.domain.exception.DaoZoneDomainException;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a Link community-governance zone — genuinely new Link
 * domain, no equivalent in L2-L5 (tnt-geo-core's zone concepts classify
 * delivery-access difficulty, they don't govern community proposals/voting).
 *
 * <p>Simplification: the zone is modelled as a circle (center + radius), not a
 * full polygon — sufficient for community governance at MVP scale; a future
 * revision could reuse tnt-geo-core's polygon model if precise boundaries
 * become necessary.
 *
 * @author Dilane PAFE
 */
@Getter
@Builder
public class DaoZone {

    private final UUID id;
    private final UUID tenantId;
    private String name;
    private String description;
    private final GeoPoint center;
    private final double radiusKm;
    private DaoZoneStatus status;
    private final UUID createdBy;
    private final Instant createdAt;
    private Instant updatedAt;

    public static DaoZone create(UUID tenantId, String name, String description,
                                  GeoPoint center, double radiusKm, UUID createdBy) {
        if (name == null || name.isBlank()) {
            throw new DaoZoneDomainException("A DAO zone requires a name");
        }
        if (center == null) {
            throw new DaoZoneDomainException("A DAO zone requires a center point");
        }
        if (radiusKm <= 0) {
            throw new DaoZoneDomainException("A DAO zone radius must be positive");
        }
        Instant now = Instant.now();
        return DaoZone.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .center(center)
                .radiusKm(radiusKm)
                .status(DaoZoneStatus.ACTIVE)
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void archive() {
        if (status == DaoZoneStatus.ARCHIVED) {
            throw new DaoZoneDomainException("Zone already archived: " + id);
        }
        this.status = DaoZoneStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    public boolean contains(GeoPoint point) {
        return center.haversineDistanceTo(point) <= radiusKm;
    }
}
