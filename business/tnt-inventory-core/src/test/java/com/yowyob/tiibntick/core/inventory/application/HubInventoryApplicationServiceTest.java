package com.yowyob.tiibntick.core.inventory.application;

import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageCommand;
import com.yowyob.tiibntick.core.inventory.application.port.out.HubPackageEntryRepository;
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

    @InjectMocks
    private HubInventoryApplicationService service;

    @Test
    void shouldDepositPackage() {
        UUID tenantId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();

        when(hubPackageRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

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

        StepVerifier.create(service.pickupPackage(trackingCode, actorId))
                .verifyComplete();
    }

    @Test
    void aDepositCarriesItsOwnerAndStorageFee() {
        UUID owner = UUID.randomUUID();
        when(hubPackageRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.depositPackage(new DepositHubPackageCommand(UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "trk-own-1", null, null, null, owner, new java.math.BigDecimal("500.00"), "XAF")))
                .assertNext(e -> {
                    org.assertj.core.api.Assertions.assertThat(e.ownerActorId()).isEqualTo(owner);
                    org.assertj.core.api.Assertions.assertThat(e.storageFee()).isEqualByComparingTo("500");
                    org.assertj.core.api.Assertions.assertThat(e.feeCurrency()).isEqualTo("XAF");
                    org.assertj.core.api.Assertions.assertThat(e.penaltyFee()).isNull();
                    org.assertj.core.api.Assertions.assertThat(e.trackingCode()).isEqualTo("TRK-OWN-1");
                }).verifyComplete();
    }

    @Test
    void pickupFindsTheEntryWhateverTheCaseOfTheTrackingCode() {
        HubPackageEntry existing = HubPackageEntry.deposit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "abc-def", null, null, null);
        when(hubPackageRepository.findByTrackingCode("ABC-DEF")).thenReturn(Mono.just(existing));
        when(hubPackageRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.pickupPackage(" abc-def ", UUID.randomUUID())).verifyComplete();
    }

    @Test
    void negativeFeesAreRefused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> HubPackageEntry.deposit(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), "T", null, null, null, null,
                        new java.math.BigDecimal("-1"), "XAF"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entriesAreReadByIdHubAndOwner() {
        UUID owner = UUID.randomUUID();
        UUID hub = UUID.randomUUID();
        HubPackageEntry e = HubPackageEntry.deposit(UUID.randomUUID(), hub, UUID.randomUUID(), "T1", null, null, null,
                owner, null, null);
        when(hubPackageRepository.findById(e.id())).thenReturn(Mono.just(e));
        when(hubPackageRepository.findAllByHub(hub)).thenReturn(reactor.core.publisher.Flux.just(e));
        when(hubPackageRepository.findAllByOwner(owner)).thenReturn(reactor.core.publisher.Flux.just(e));

        StepVerifier.create(service.findEntry(e.id())).expectNext(e).verifyComplete();
        StepVerifier.create(service.findEntriesOfHub(hub)).expectNext(e).verifyComplete();
        StepVerifier.create(service.findEntriesOfOwner(owner)).expectNext(e).verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("D-140: a hub that is not approved refuses the deposit (409), nothing saved nor published")
    void depositRefusedInAHubThatIsNotApproved() {
        UUID hubId = UUID.randomUUID();
        service.setHubDepositGuard(id -> Mono.error(
                new com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException("Hub " + id + " is SUSPENDED")));
        DepositHubPackageCommand cmd = new DepositHubPackageCommand(UUID.randomUUID(), hubId, UUID.randomUUID(),
                "TNT-1", null, UUID.randomUUID(), null);

        StepVerifier.create(service.depositPackage(cmd))
                .expectError(com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException.class).verify();
        org.mockito.Mockito.verifyNoInteractions(hubPackageRepository);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("D-140: an approved hub (guard completes) takes the deposit; pick-ups are never guarded")
    void depositAcceptedInAnApprovedHub() {
        java.util.List<UUID> checked = new java.util.ArrayList<>();
        service.setHubDepositGuard(id -> Mono.fromRunnable(() -> checked.add(id)));
        UUID hubId = UUID.randomUUID();
        when(hubPackageRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.depositPackage(new DepositHubPackageCommand(UUID.randomUUID(), hubId,
                        UUID.randomUUID(), "TNT-2", null, UUID.randomUUID(), null)))
                .assertNext(e -> assertThat(e.hubId()).isEqualTo(hubId))
                .verifyComplete();
        assertThat(checked).containsExactly(hubId);

        HubPackageEntry held = HubPackageEntry.deposit(UUID.randomUUID(), hubId, UUID.randomUUID(), "TNT-3", null,
                null, null);
        when(hubPackageRepository.findByTrackingCode("TNT-3")).thenReturn(Mono.just(held));
        StepVerifier.create(service.pickupPackage("TNT-3", UUID.randomUUID())).verifyComplete();
        assertThat(checked).as("the pick-up does not ask the hub").containsExactly(hubId);
    }
}
