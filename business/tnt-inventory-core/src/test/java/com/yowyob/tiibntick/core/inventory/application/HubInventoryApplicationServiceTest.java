package com.yowyob.tiibntick.core.inventory.application;

import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageCommand;
import com.yowyob.tiibntick.core.inventory.application.port.out.HubPackageEntryRepository;
import com.yowyob.tiibntick.core.inventory.application.port.out.InventoryEventPublisherPort;
import com.yowyob.tiibntick.core.inventory.application.service.HubInventoryApplicationService;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HubInventoryApplicationServiceTest {

    @Mock
    private HubPackageEntryRepository hubPackageRepository;
    @Mock
    private InventoryEventPublisherPort eventPublisher;

    @InjectMocks
    private HubInventoryApplicationService service;

    @Test
    void shouldDepositPackage() {
        UUID tenantId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();

        when(hubPackageRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishPackageDeposited(any())).thenReturn(Mono.empty());

        DepositHubPackageCommand cmd = new DepositHubPackageCommand(
                tenantId, hubId, packageId, "TNT-2026-CMR001",
                "Shelf A-3", UUID.randomUUID(), "+237699000001");

        StepVerifier.create(service.depositPackage(cmd))
                .assertNext(entry -> {
                    assertThat(entry.trackingCode()).isEqualTo("TNT-2026-CMR001");
                    assertThat(entry.hubId()).isEqualTo(hubId);
                    assertThat(entry.isPickedUp()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void shouldComputeOccupancyRate() {
        UUID hubId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        HubPackageEntry pkg1 = HubPackageEntry.deposit(tenantId, hubId, UUID.randomUUID(),
                "TRK-001", "A1", null, "+237699000001");
        HubPackageEntry pkg2 = HubPackageEntry.deposit(tenantId, hubId, UUID.randomUUID(),
                "TRK-002", "A2", null, "+237699000002");

        when(hubPackageRepository.findActiveByHub(hubId)).thenReturn(Flux.just(pkg1, pkg2));

        StepVerifier.create(service.getOccupancyRate(hubId, 10))
                .assertNext(rate -> assertThat(rate).isEqualTo(0.2)) // 2 / 10
                .verifyComplete();
    }

    @Test
    void shouldFindOverduePackages() {
        UUID hubId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Simulate package deposited 100 hours ago by rehydration
        HubPackageEntry overdue = HubPackageEntry.rehydrate(
                UUID.randomUUID(), tenantId, hubId, UUID.randomUUID(),
                "TRK-OLD-001", "B5",
                Instant.now().minusSeconds(100 * 3600L), // deposited 100h ago
                null, null, null, "+237699000003", false);

        HubPackageEntry fresh = HubPackageEntry.deposit(tenantId, hubId, UUID.randomUUID(),
                "TRK-NEW-002", "B6", null, "+237699000004");

        when(hubPackageRepository.findActiveByHub(hubId)).thenReturn(Flux.just(overdue, fresh));

        StepVerifier.create(service.findOverduePackages(hubId, 48))
                .assertNext(entry -> assertThat(entry.trackingCode()).isEqualTo("TRK-OLD-001"))
                .verifyComplete();
    }

    @Test
    void shouldPickupPackage() {
        UUID hubId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String trackingCode = "TRK-PICKUP-001";

        HubPackageEntry existing = HubPackageEntry.deposit(tenantId, hubId, UUID.randomUUID(),
                trackingCode, "C1", null, "+237699000005");

        when(hubPackageRepository.findByTrackingCode(trackingCode)).thenReturn(Mono.just(existing));
        when(hubPackageRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishPackagePickedUp(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.pickupPackage(trackingCode, actorId))
                .verifyComplete();
    }
}
