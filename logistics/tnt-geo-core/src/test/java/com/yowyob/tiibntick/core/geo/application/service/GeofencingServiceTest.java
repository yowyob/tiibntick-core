package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.out.IGeoEventPublisher;
import com.yowyob.tiibntick.core.geo.application.port.out.IServiceZoneRepository;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.ServiceZonePolygon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GeofencingService — zone containment checks.
 *
 * Author: MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class GeofencingServiceTest {

    @Mock private IServiceZoneRepository zoneRepository;
    @Mock private IGeoEventPublisher eventPublisher;

    private GeofencingService geofencingService;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID AGENCY = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        geofencingService = new GeofencingService(zoneRepository, eventPublisher);
    }

    private ServiceZonePolygon buildYaoundeZone() {
        return ServiceZonePolygon.create(TENANT, AGENCY, "Yaoundé Centre", List.of(
                GeoPoint.of(3.8, 11.4),
                GeoPoint.of(3.8, 11.6),
                GeoPoint.of(4.0, 11.6),
                GeoPoint.of(4.0, 11.4)
        ));
    }

    @Test
    void isPointInZone_pointInside_returnsTrue() {
        ServiceZonePolygon zone = buildYaoundeZone();
        when(zoneRepository.findById(zone.id(), TENANT)).thenReturn(Mono.just(zone));

        StepVerifier.create(
                geofencingService.isPointInZone(GeoPoint.of(3.9, 11.5), zone.id(), TENANT)
        )
        .expectNext(true)
        .verifyComplete();
    }

    @Test
    void isPointInZone_pointOutside_returnsFalse() {
        ServiceZonePolygon zone = buildYaoundeZone();
        when(zoneRepository.findById(zone.id(), TENANT)).thenReturn(Mono.just(zone));

        StepVerifier.create(
                geofencingService.isPointInZone(GeoPoint.of(5.0, 11.5), zone.id(), TENANT)
        )
        .expectNext(false)
        .verifyComplete();
    }

    @Test
    void isPointInZone_zoneBelongsToAnotherTenant_isNotFound_neverLeaksAcrossTenants() {
        // Regression test for Audit n°7 · #19: findById(id, tenantId) must scope the
        // lookup to the caller's tenant. A zone that exists but belongs to a different
        // tenant must surface as "not found", not as a leaked cross-tenant result.
        ServiceZonePolygon zone = buildYaoundeZone();
        UUID otherTenant = UUID.randomUUID();
        // The repository is tenant-scoped: querying with a different tenant than the
        // zone's owner returns empty (this is what a correct WHERE tenant_id = :tenantId
        // clause produces).
        when(zoneRepository.findById(zone.id(), otherTenant)).thenReturn(Mono.empty());

        StepVerifier.create(
                geofencingService.isPointInZone(GeoPoint.of(3.9, 11.5), zone.id(), otherTenant)
        )
        .expectError(GeoNotFoundException.class)
        .verify();
    }

    @Test
    void isPointCoveredByAgency_multipleZones_trueIfAnyContains() {
        ServiceZonePolygon zone1 = buildYaoundeZone();
        ServiceZonePolygon zone2 = ServiceZonePolygon.create(TENANT, AGENCY, "Zone Est",
                List.of(
                        GeoPoint.of(4.5, 12.0),
                        GeoPoint.of(4.5, 12.3),
                        GeoPoint.of(4.7, 12.3),
                        GeoPoint.of(4.7, 12.0)
                )
        );
        when(zoneRepository.findByAgency(AGENCY, TENANT)).thenReturn(Flux.just(zone1, zone2));

        StepVerifier.create(
                geofencingService.isPointCoveredByAgency(GeoPoint.of(3.9, 11.5), AGENCY, TENANT)
        )
        .expectNext(true)
        .verifyComplete();
    }

    @Test
    void findContainingZone_noMatchingZone_emitsEmpty() {
        when(zoneRepository.findAllActiveByTenant(TENANT)).thenReturn(Flux.empty());

        StepVerifier.create(
                geofencingService.findContainingZone(GeoPoint.of(10.0, 20.0), TENANT)
        )
        .verifyComplete();
    }
}
