package com.yowyob.tiibntick.core.inventory.application.service;

import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageCommand;
import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.FindOverduePackagesUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.GetHubOccupancyUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.PickupHubPackageUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.out.HubPackageEntryRepository;
import com.yowyob.tiibntick.core.inventory.application.port.out.InventoryEventPublisherPort;
import com.yowyob.tiibntick.core.inventory.domain.event.PackageDepositedEvent;
import com.yowyob.tiibntick.core.inventory.domain.event.PackagePickedUpEvent;
import com.yowyob.tiibntick.core.inventory.domain.exception.HubPackageNotFoundException;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for relay hub package management (TiiBnTick Point integration).
 *
 * Manages the full lifecycle of packages deposited at relay hubs:
 * deposit, pickup confirmation, overdue detection, and occupancy monitoring.
 * All major operations emit Kafka events for TiiBnTick Trust blockchain anchoring.
 *
 * @author MANFOUO Braun.
 */
@Service
public class HubInventoryApplicationService implements
        DepositHubPackageUseCase,
        PickupHubPackageUseCase,
        GetHubOccupancyUseCase,
        FindOverduePackagesUseCase {

    private final HubPackageEntryRepository hubPackageRepository;
    private final InventoryEventPublisherPort eventPublisher;

    public HubInventoryApplicationService(HubPackageEntryRepository hubPackageRepository,
                                           InventoryEventPublisherPort eventPublisher) {
        this.hubPackageRepository = hubPackageRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @Override
    public Mono<HubPackageEntry> depositPackage(DepositHubPackageCommand cmd) {
        HubPackageEntry entry = HubPackageEntry.deposit(
                cmd.tenantId(), cmd.hubId(), cmd.packageId(), cmd.trackingCode(),
                cmd.storageLocation(), cmd.depositedByActorId(), cmd.recipientPhone());
        return hubPackageRepository.save(entry)
                .flatMap(saved -> eventPublisher.publishPackageDeposited(
                        PackageDepositedEvent.of(saved.id(), saved.hubId(), saved.packageId(),
                                saved.trackingCode(), saved.tenantId()))
                        .thenReturn(saved));
    }

    @Transactional
    @Override
    public Mono<Void> pickupPackage(String trackingCode, UUID pickedUpByActorId) {
        return hubPackageRepository.findByTrackingCode(trackingCode)
                .switchIfEmpty(Mono.error(new HubPackageNotFoundException(trackingCode)))
                .flatMap(entry -> {
                    HubPackageEntry updated = entry.markPickedUp(pickedUpByActorId);
                    return hubPackageRepository.save(updated)
                            .flatMap(saved -> eventPublisher.publishPackagePickedUp(
                                    PackagePickedUpEvent.of(saved.id(), saved.hubId(),
                                            saved.trackingCode(), saved.tenantId())));
                })
                .then();
    }

    /**
     * Returns the occupancy rate of a hub as a value between 0.0 and 1.0.
     *
     * @param hubId       the hub to measure
     * @param maxCapacity physical package capacity of the hub
     * @return occupancy rate (0.0 = empty, 1.0 = full)
     */
    @Override
    public Mono<Double> getOccupancyRate(UUID hubId, int maxCapacity) {
        if (maxCapacity <= 0) return Mono.just(0.0);
        return hubPackageRepository.findActiveByHub(hubId)
                .count()
                .map(count -> Math.min(1.0, (double) count / maxCapacity));
    }

    /**
     * Finds packages that have been in the hub longer than maxHours without pickup.
     *
     * @param hubId    hub to scan
     * @param maxHours allowed storage duration in hours before overdue
     * @return stream of overdue HubPackageEntry
     */
    @Override
    public Flux<HubPackageEntry> findOverduePackages(UUID hubId, long maxHours) {
        return hubPackageRepository.findActiveByHub(hubId)
                .filter(entry -> entry.isOverdue(maxHours));
    }
}
