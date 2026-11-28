package com.yowyob.tiibntick.core.inventory.application.service;

import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageCommand;
import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.FindHubPackagesUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.FindOverduePackagesUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.GetHubOccupancyUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.PickupHubPackageUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.out.HubPackageEntryRepository;
import com.yowyob.tiibntick.core.inventory.domain.exception.HubPackageNotFoundException;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import com.yowyob.tiibntick.core.geo.application.port.in.ICheckHubAcceptsDepositsUseCase;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Lot L8: no Kafka event of its own any more — the parcel's entry into and exit from a relay hub are its
 * {@code tnt.parcel.events} transitions (delivery-core).
 *
 * @author MANFOUO Braun.
 */
@Service
public class HubInventoryApplicationService implements
        DepositHubPackageUseCase,
        PickupHubPackageUseCase,
        GetHubOccupancyUseCase,
        FindOverduePackagesUseCase,
        FindHubPackagesUseCase {

    private final HubPackageEntryRepository hubPackageRepository;

    /**
     * Approval of the hub in geo-core (D-140): a hub that is not APPROVED refuses every new deposit. Optional so the
     * service still builds without geo-core (tests, other assemblies); a hub unknown to geo-core is not governed.
     */
    private ICheckHubAcceptsDepositsUseCase hubDepositGuard;

    public HubInventoryApplicationService(HubPackageEntryRepository hubPackageRepository) {
        this.hubPackageRepository = hubPackageRepository;
    }

    @Autowired(required = false)
    public void setHubDepositGuard(ICheckHubAcceptsDepositsUseCase hubDepositGuard) {
        this.hubDepositGuard = hubDepositGuard;
    }

    /**
     * Records a new deposit, refused (409, {@code RelayHubStateException}) when the hub is pending, suspended,
     * rejected or revoked (D-140). Pick-ups are never refused: parcels already held leave until the end of their
     * storage period. Their return at the end of that period is row D12 of delivery-core
     * ({@code RelayStorageService.expireStorage}), the only return path; {@link #findOverduePackages} only reads.
     */
    @Transactional
    @Override
    public Mono<HubPackageEntry> depositPackage(DepositHubPackageCommand cmd) {
        Mono<Void> guard = hubDepositGuard == null ? Mono.empty() : hubDepositGuard.requireAcceptsDeposits(cmd.hubId());
        return guard.then(Mono.defer(() -> save(cmd)));
    }

    private Mono<HubPackageEntry> save(DepositHubPackageCommand cmd) {
        HubPackageEntry entry = HubPackageEntry.deposit(
                cmd.tenantId(), cmd.hubId(), cmd.packageId(), cmd.trackingCode(),
                cmd.storageLocation(), cmd.depositedByActorId(), cmd.recipientPhone(),
                cmd.ownerActorId(), cmd.storageFee(), cmd.feeCurrency());
        return hubPackageRepository.save(entry);
    }

    @Transactional
    @Override
    public Mono<Void> pickupPackage(String trackingCode, UUID pickedUpByActorId) {
        // Tracking codes are stored upper-cased (HubPackageEntry): look them up the same way.
        String normalized = trackingCode == null ? null : trackingCode.strip().toUpperCase();
        return hubPackageRepository.findByTrackingCode(normalized)
                .switchIfEmpty(Mono.error(new HubPackageNotFoundException(trackingCode)))
                .flatMap(entry -> {
                    HubPackageEntry updated = entry.markPickedUp(pickedUpByActorId);
                    return hubPackageRepository.save(updated);
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
     * Finds packages that have been in the hub longer than maxHours without pickup. Read-only: it never starts a
     * return — the end of the relay storage is handled by delivery-core (row D12, {@code RelayStorageService}, lot
     * evt-l2), the single return path.
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

    @Override
    public Mono<HubPackageEntry> findEntry(UUID entryId) {
        return hubPackageRepository.findById(entryId);
    }

    @Override
    public Flux<HubPackageEntry> findEntriesOfHub(UUID hubId) {
        return hubPackageRepository.findAllByHub(hubId);
    }

    @Override
    public Flux<HubPackageEntry> findEntriesOfOwner(UUID ownerActorId) {
        return hubPackageRepository.findAllByOwner(ownerActorId);
    }
}
