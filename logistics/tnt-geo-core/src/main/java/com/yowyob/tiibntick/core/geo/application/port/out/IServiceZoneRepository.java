package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.ServiceZonePolygon;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — persistence for service zone polygons.
 *
 * Author: MANFOUO Braun
 */
public interface IServiceZoneRepository {

    Mono<ServiceZonePolygon> save(ServiceZonePolygon zone);

    Mono<ServiceZonePolygon> findById(UUID id, UUID tenantId);

    Flux<ServiceZonePolygon> findByAgency(UUID agencyId, UUID tenantId);

    Flux<ServiceZonePolygon> findAllActiveByTenant(UUID tenantId);
    /**
     * Finds all active service zones owned by a specific FreelancerOrg ().
     *
     * @param freelancerOrgId the FreelancerOrg UUID
     * @param tenantId        tenant scope
     * @return Flux of active zones for this FreelancerOrg
     */
    reactor.core.publisher.Flux<ServiceZonePolygon> findByFreelancerOrg(String freelancerOrgId, java.util.UUID tenantId);

    /**
     * Finds all active service zones that contain the given coordinate,
     * filtered by owner type FREELANCER_ORG ().
     *
     * @param tenantId tenant scope
     * @return Flux of all active FreelancerOrg zones for the tenant
     */
    reactor.core.publisher.Flux<ServiceZonePolygon> findAllActiveFreelancerOrgZonesByTenant(java.util.UUID tenantId);

}