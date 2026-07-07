package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.IFreelancerOrgGeoUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IGeoEventPublisher;
import com.yowyob.tiibntick.core.geo.application.port.out.IServiceZoneRepository;
import com.yowyob.tiibntick.core.geo.application.port.out.IRoadArcRepository;
import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Application service implementing geographic operations for FreelancerOrganizations.
 *
 * <p>Provides the computation logic for two key DSL billing context variables:
 * <ul>
 *   <li>{@code deliveryZoneType} — classifies the destination as URBAN/PERI_URBAN/RURAL/etc.</li>
 *   <li>{@code zoneAccessDifficulty} — estimates road access difficulty at destination.</li>
 * </ul>
 *
 * <p>Also manages FreelancerOrg service zone registration and lookup,
 * enabling the announcement marketplace to match clients with eligible freelancers.
 *
 * <p><b>Kernel integration principle:</b> The {@code freelancerOrgId} is stored as a plain
 * String UUID — no join to tnt-organization-core. All zone ownership is determined
 * by the {@code owner_type} column in {@code geo_service_zones}.
 *
 * @author MANFOUO Braun
 */
@Service
public class FreelancerOrgGeoService implements IFreelancerOrgGeoUseCase {

    private static final Logger log = LoggerFactory.getLogger(FreelancerOrgGeoService.class);

    private final IServiceZoneRepository serviceZoneRepository;
    private final IRoadArcRepository roadArcRepository;
    private final IGeoEventPublisher eventPublisher;

    public FreelancerOrgGeoService(IServiceZoneRepository serviceZoneRepository,
                                    IRoadArcRepository roadArcRepository,
                                    IGeoEventPublisher eventPublisher) {
        this.serviceZoneRepository = serviceZoneRepository;
        this.roadArcRepository = roadArcRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation strategy:
     * <ol>
     *   <li>Queries the nearest road arc to the given coordinate.</li>
     *   <li>Maps the arc's road type to a base difficulty level.</li>
     *   <li>Adjusts for zone risk index if available from geo snapshot data.</li>
     * </ol>
     */
    @Override
    public Mono<ZoneAccessDifficulty> computeZoneAccessDifficulty(double lat, double lng) {
        log.debug("Computing zone access difficulty for ({}, {})", lat, lng);
        // Find the nearest road arc and derive difficulty from its road type
        return roadArcRepository.findNearestToCoordinate(lat, lng, 2.0)
                .map(arc -> mapRoadTypeToDifficulty(arc.roadType()))
                .next()
                .defaultIfEmpty(ZoneAccessDifficulty.MODERATE)
                .doOnNext(d -> log.debug("Zone access difficulty at ({},{}) = {}", lat, lng, d));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation strategy:
     * <ol>
     *   <li>Check if the coordinate falls within any active urban service zone → URBAN.</li>
     *   <li>Check proximity to city boundaries → PERI_URBAN.</li>
     *   <li>Fallback: infer from road arc density → RURAL or REMOTE.</li>
     * </ol>
     */
    @Override
    public Mono<DeliveryZoneType> resolveDeliveryZoneType(double lat, double lng) {
        log.debug("Resolving delivery zone type for ({}, {})", lat, lng);
        //GeoPoint point = GeoPoint.of(lat, lng);

        // Strategy: check road arc count within radius to infer urban density
        return roadArcRepository.findNearestToCoordinate(lat, lng, 1.0)
                .count()
                .map(count -> {
                    if (count >= 5) return DeliveryZoneType.URBAN;
                    if (count >= 2) return DeliveryZoneType.PERI_URBAN;
                    if (count == 1) return DeliveryZoneType.RURAL;
                    return DeliveryZoneType.REMOTE;
                })
                .doOnNext(z -> log.debug("Delivery zone type at ({},{}) = {}", lat, lng, z));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Finds all FreelancerOrg service zones for the tenant and tests point containment.
     */
    @Override
    public Flux<String> findFreelancerOrgsInZone(double lat, double lng, double radiusKm, UUID tenantId) {
        log.debug("Finding FreelancerOrgs in zone for ({}, {}) radiusKm={} tenant={}", lat, lng, radiusKm, tenantId);
        GeoPoint point = GeoPoint.of(lat, lng);
        return serviceZoneRepository.findAllActiveFreelancerOrgZonesByTenant(tenantId)
                .filter(zone -> zone.contains(point) || isWithinRadius(zone, point, radiusKm))
                .mapNotNull(ServiceZonePolygon::freelancerOrgId)
                .distinct();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Boolean> isInFreelancerOrgZone(String freelancerOrgId, double lat, double lng, UUID tenantId) {
        log.debug("Checking if ({},{}) is in FreelancerOrg={} zone", lat, lng, freelancerOrgId);
        GeoPoint point = GeoPoint.of(lat, lng);
        return serviceZoneRepository.findByFreelancerOrg(freelancerOrgId, tenantId)
                .any(zone -> zone.isActive() && zone.contains(point));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<ServiceZonePolygon> defineFreelancerOrgZone(UUID tenantId, String freelancerOrgId,
                                                             String name, List<GeoPoint> vertices) {
        log.info("Defining service zone for FreelancerOrg={} tenant={} name={}", freelancerOrgId, tenantId, name);
        ServiceZonePolygon zone = ServiceZonePolygon.createForFreelancerOrg(tenantId, freelancerOrgId, name, vertices);
        return serviceZoneRepository.save(zone)
                .doOnSuccess(z -> log.info("FreelancerOrg zone created: id={} orgId={}", z.id(), freelancerOrgId));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private ZoneAccessDifficulty mapRoadTypeToDifficulty(RoadType roadType) {
        if (roadType == null) return ZoneAccessDifficulty.MODERATE;
        return switch (roadType) {
            case HIGHWAY, PAVED -> ZoneAccessDifficulty.EASY;
            case DEGRADED -> ZoneAccessDifficulty.DIFFICULT;
            case DIRT -> ZoneAccessDifficulty.DIFFICULT;
            default -> ZoneAccessDifficulty.MODERATE;
        };
    }

    /**
     * Basic radius check: approximates whether any vertex of the zone polygon
     * is within {@code radiusKm} of the given point.
     * Uses the Haversine approximation for sub-100km distances.
     */
    private boolean isWithinRadius(ServiceZonePolygon zone, GeoPoint point, double radiusKm) {
        if (radiusKm <= 0) return false;
        return zone.vertices().stream().anyMatch(v -> haversineKm(v, point) <= radiusKm);
    }

    private double haversineKm(GeoPoint a, GeoPoint b) {
        final double R = 6371.0;
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLng = Math.toRadians(b.longitude() - a.longitude());
        double sinLat = Math.sin(dLat / 2);
        double sinLng = Math.sin(dLng / 2);
        double c = 2 * Math.asin(Math.sqrt(sinLat * sinLat
                + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude()))
                * sinLng * sinLng));
        return R * c;
    }
}
