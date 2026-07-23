package com.yowyob.tiibntick.core.organization.application.service;

import com.yowyob.tiibntick.core.organization.application.port.in.ManageHubUseCase;
import com.yowyob.tiibntick.core.organization.application.port.out.HubEventPublisherPort;
import com.yowyob.tiibntick.core.organization.application.port.out.HubRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.domain.event.HubRelaisUpdatedEvent;
import com.yowyob.tiibntick.core.organization.domain.model.HubRelais;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing {@link ManageHubUseCase}.
 *
 * <p>Orchestrates HubRelais aggregate lifecycle:
 * <ol>
 *   <li>Validates the Kernel organization reference via {@link KernelOrganizationPort}
 *       before any creation.</li>
 *   <li>Enforces TiiBnTick business rules (capacity &gt; 0, operator nullable).</li>
 *   <li>Delegates persistence and PostGIS geo-queries to {@link HubRepositoryPort}.</li>
 * </ol>
 *
 * <p>No {@code @Service} annotation — Spring wiring is done in
 * {@link com.yowyob.tiibntick.core.organization.config.OrganizationCoreAutoConfiguration}.
 *
 * <h3>Security ( — tnt-roles-core integration)</h3>
 * <p>Relay hub operations are mapped to the {@code relay} permission resource:
 * <ul>
 *   <li>{@code relay:write} — creating a hub</li>
 *   <li>{@code relay:read} — reading hub data and capacity check</li>
 *   <li>{@code relay:operate} — zone-scoped operational queries (for relay operators)</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class HubRelaisService implements ManageHubUseCase {

    private final HubRepositoryPort hubRepository;
    private final KernelOrganizationPort kernelOrganizationPort;
    private final HubEventPublisherPort eventPublisher;

    /**
     * Constructor injection.
     *
     * @param hubRepository          persistence port for HubRelais aggregates
     * @param kernelOrganizationPort outbound Kernel integration port
     * @param eventPublisher         outbound port for HubRelais domain events
     */
    public HubRelaisService(HubRepositoryPort hubRepository,
                            KernelOrganizationPort kernelOrganizationPort,
                            HubEventPublisherPort eventPublisher) {
        this.hubRepository = hubRepository;
        this.kernelOrganizationPort = kernelOrganizationPort;
        this.eventPublisher = eventPublisher;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates Kernel organization existence before creating the HubRelais.
     * Requires permission: {@code relay:write}.
     */
    @Override
    @RequirePermission(resource = "relay", action = "write")
    public Mono<HubRelais> createHub(UUID organizationId,
                                     UUID tenantId,
                                     String name,
                                     int maxParcelCapacity,
                                     String geographicPointWkt,
                                     String openingHours,
                                     UUID operatorId) {
        return kernelOrganizationPort.existsAndActive(organizationId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Kernel organization not found or inactive: " + organizationId));
                    }
                    HubRelais hub = HubRelais.create(
                            organizationId, tenantId, name, maxParcelCapacity,
                            geographicPointWkt, openingHours, operatorId);
                    return hubRepository.save(hub);
                });
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code relay:read}.
     */
    @Override
    @RequirePermission(resource = "relay", action = "read")
    public Mono<HubRelais> findHubById(OrganizationId hubId) {
        return hubRepository.findById(hubId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fetches the hub and delegates the capacity check to the domain method
     * {@link HubRelais#hasAvailableCapacity(int)}.
     * Requires permission: {@code relay:read}.
     */
    @Override
    @RequirePermission(resource = "relay", action = "read")
    public Mono<Boolean> checkHubCapacity(OrganizationId hubId, int currentOccupancy) {
        return hubRepository.findById(hubId)
                .map(hub -> hub.hasAvailableCapacity(currentOccupancy))
                .defaultIfEmpty(false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>PostGIS geo-query: finds all active hubs within a given WKT polygon.
     * Requires permission: {@code relay:operate} — available to relay operators
     * and agency managers who need zone-level visibility.
     */
    @Override
    @RequirePermission(resource = "relay", action = "operate")
    public Flux<HubRelais> findHubsInZone(String polygonWkt) {
        return hubRepository.findWithinPolygon(polygonWkt);
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code relay:read}.
     */
    @Override
    @RequirePermission(resource = "relay", action = "read")
    public Flux<HubRelais> listHubsForTenant(UUID tenantId) {
        return hubRepository.findAllByTenantId(tenantId);
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code relay:write}.
     */
    @Override
    @RequirePermission(resource = "relay", action = "write")
    public Mono<HubRelais> updateCapacity(OrganizationId hubId, int newCapacity) {
        return hubRepository.findById(hubId)
                .flatMap(hub -> {
                    hub.updateCapacity(newCapacity);
                    return saveAndPublish(hub, "CAPACITY_UPDATED");
                });
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code relay:write}.
     */
    @Override
    @RequirePermission(resource = "relay", action = "write")
    public Mono<HubRelais> assignOperator(OrganizationId hubId, UUID operatorId) {
        return hubRepository.findById(hubId)
                .flatMap(hub -> {
                    hub.assignOperator(operatorId);
                    return saveAndPublish(hub, "OPERATOR_ASSIGNED");
                });
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code relay:write}.
     */
    @Override
    @RequirePermission(resource = "relay", action = "write")
    public Mono<HubRelais> suspendHub(OrganizationId hubId) {
        return hubRepository.findById(hubId)
                .flatMap(hub -> {
                    hub.suspend();
                    return saveAndPublish(hub, "SUSPENDED");
                });
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code relay:write}.
     */
    @Override
    @RequirePermission(resource = "relay", action = "write")
    public Mono<HubRelais> resumeHub(OrganizationId hubId) {
        return hubRepository.findById(hubId)
                .flatMap(hub -> {
                    hub.resume();
                    return saveAndPublish(hub, "RESUMED");
                });
    }

    private Mono<HubRelais> saveAndPublish(HubRelais hub, String updateReason) {
        return hubRepository.save(hub)
                .flatMap(saved -> eventPublisher.publishHubUpdated(HubRelaisUpdatedEvent.of(
                                saved.getId().value(), saved.getTenantId(), updateReason))
                        .thenReturn(saved));
    }
}
