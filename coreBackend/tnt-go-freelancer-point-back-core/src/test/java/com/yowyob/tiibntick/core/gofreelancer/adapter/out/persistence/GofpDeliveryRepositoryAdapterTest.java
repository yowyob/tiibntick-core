package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L1: the gofp delivery row is an extension of the core delivery. Every row read is filled from the
 * core; every query on the delivery's own facts is answered by the core; nothing about them is written.
 */
class GofpDeliveryRepositoryAdapterTest {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryRepository rows =
            mock(com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryRepository.class);
    private final IDeliveryLifecyclePort core = mock(IDeliveryLifecyclePort.class);
    private final TenantContextHolder tenants = mock(TenantContextHolder.class);
    private final GofpDeliveryRepositoryAdapter adapter = new GofpDeliveryRepositoryAdapter(rows, core, tenants);

    private final UUID tenant = UUID.randomUUID();
    private final UUID courier = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        when(tenants.currentTenantId()).thenReturn(Mono.just(tenant));
    }

    private static Delivery row(UUID id) {
        Delivery d = new Delivery();
        d.setId(id);
        d.setPickupOtpHash("hash");
        return d;
    }

    private CoreDeliveryState state(UUID id, String status, Double volume) {
        return new CoreDeliveryState(id, status, "TNT-1", courier, start, null, new BigDecimal("2500.00"), "XAF",
                start, start.plusSeconds(3600), start.plusSeconds(7200), start.plusSeconds(86400), null,
                UUID.randomUUID(), volume);
    }

    @Test
    @DisplayName("findById: the extension row carries the core status, courier, price and windows")
    void findByIdIsHydrated() {
        UUID id = UUID.randomUUID();
        when(rows.findById(id)).thenReturn(Mono.just(row(id)));
        when(core.findStateAnyTenant(id)).thenReturn(Mono.just(state(id, "IN_TRANSIT", 0.006)));

        StepVerifier.create(adapter.findById(id)).assertNext(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
            assertThat(d.getFreelancerId()).isEqualTo(courier);
            assertThat(d.getTarif()).isEqualTo(2500.0);
            assertThat(d.getPickupMinTime()).isEqualTo(start);
            assertThat(d.getDeliveryMaxTime()).isEqualTo(start.plusSeconds(86400));
            assertThat(d.getActualPickupTime()).isEqualTo(start);
            assertThat(d.getPickupOtpHash()).as("extension columns kept").isEqualTo("hash");
        }).verifyComplete();
    }

    @Test
    @DisplayName("a row the core does not know is returned bare (no status), never dropped")
    void unknownToTheCore() {
        UUID id = UUID.randomUUID();
        when(rows.findByDeliveryNeedId(id)).thenReturn(Mono.just(row(id)));
        when(core.findStateAnyTenant(id)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findByDeliveryNeedId(id))
                .assertNext(d -> assertThat(d.getStatus()).isNull()).verifyComplete();
    }

    @Test
    @DisplayName("courier's deliveries: listed by the core in the caller's tenant, only those gofp extends, core order kept")
    void byCourierFromTheCore() {
        UUID first = UUID.randomUUID();
        UUID agencyOnly = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(core.findByDeliveryPerson(tenant, courier)).thenReturn(Flux.just(
                state(first, "CREATED", null), state(agencyOnly, "CREATED", null), state(second, "DELIVERED", null)));
        when(rows.findAllById(anyIterable())).thenReturn(Flux.just(row(second), row(first)));

        StepVerifier.create(adapter.findAllByFreelancerId(courier).map(Delivery::getId))
                .expectNext(first, second).verifyComplete();
    }

    @Test
    @DisplayName("active deliveries are the courier's PICKED_UP / IN_TRANSIT ones in the core")
    void activeFromTheCore() {
        UUID picked = UUID.randomUUID();
        UUID created = UUID.randomUUID();
        UUID moving = UUID.randomUUID();
        when(core.findByDeliveryPerson(tenant, courier)).thenReturn(Flux.just(
                state(picked, "PICKED_UP", null), state(created, "CREATED", null), state(moving, "IN_TRANSIT", null)));
        when(rows.findAllById(anyIterable())).thenReturn(Flux.just(row(picked), row(moving)));

        StepVerifier.create(adapter.findActiveByFreelancerId(courier).map(Delivery::getStatus))
                .expectNext(DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT).verifyComplete();
    }

    @Test
    @DisplayName("no tenant in context: the system tenant is used")
    void systemTenantFallback() {
        when(tenants.currentTenantId()).thenReturn(Mono.empty());
        when(core.findByStatus(TenantContextHolder.SYSTEM_TENANT, DeliveryStatus.CREATED)).thenReturn(Flux.empty());

        StepVerifier.create(adapter.findAllByStatus(DeliveryStatus.CREATED)).verifyComplete();
        verify(core).findByStatus(TenantContextHolder.SYSTEM_TENANT, DeliveryStatus.CREATED);
    }

    @Test
    @DisplayName("counts are the core's; an empty courier list does not reach it")
    void countsFromTheCore() {
        List<UUID> ids = List.of(courier);
        when(core.countUnfinished(tenant, ids)).thenReturn(Mono.just(3L));
        when(core.countDelivered(tenant, ids)).thenReturn(Mono.just(7L));

        StepVerifier.create(adapter.countUnfinishedByFreelancerIds(ids)).expectNext(3L).verifyComplete();
        StepVerifier.create(adapter.countDeliveredByFreelancerIds(ids)).expectNext(7L).verifyComplete();
        StepVerifier.create(adapter.countUnfinishedByFreelancerIds(List.of())).expectNext(0L).verifyComplete();
        verify(core, never()).countUnfinished(tenant, List.of());
    }

    @Test
    @DisplayName("occupied trunk volume (m³): sum of the parcels of the active deliveries gofp extends")
    void occupiedVolume() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID done = UUID.randomUUID();
        UUID agencyOnly = UUID.randomUUID();
        when(core.findByDeliveryPerson(tenant, courier)).thenReturn(Flux.just(
                state(a, "PICKED_UP", 0.006), state(b, "IN_TRANSIT", 0.5), state(done, "DELIVERED", 9.0),
                state(agencyOnly, "IN_TRANSIT", 4.0)));
        when(rows.findAllById(anyIterable())).thenReturn(Flux.just(row(a), row(b)));

        StepVerifier.create(adapter.sumOccupiedVolumeM3ByFreelancerId(courier))
                .assertNext(v -> assertThat(v).isCloseTo(0.506, org.assertj.core.data.Offset.offset(1e-9)))
                .verifyComplete();
    }

    // ── C1: a relay deposit finds its delivery by the parcel id, whatever parcel the core created ──

    @Test
    @DisplayName("parcel lookup, need created WITHOUT a parcel: the core created its own parcel, the deposit keyed on it finds the delivery")
    void parcelOfTheCore_needWithoutParcel() {
        UUID coreParcel = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        when(core.findStatesByParcelAnyTenant(coreParcel)).thenReturn(Flux.just(state(deliveryId, "AT_RELAY_HUB", null)));
        when(rows.findById(deliveryId)).thenReturn(Mono.just(row(deliveryId)));

        StepVerifier.create(adapter.findByParcelId(coreParcel)).assertNext(d -> {
            assertThat(d.getId()).isEqualTo(deliveryId);
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.AT_RELAY_HUB);
        }).verifyComplete();
        verify(rows, never()).findByParcelId(any());
    }

    @Test
    @DisplayName("parcel lookup, need published BEFORE L1: a deposit made now is keyed on the core's copy of the parcel and is found")
    void parcelOfTheCore_needPublishedBeforeL1() {
        UUID needPacket = UUID.randomUUID();
        UUID coreCopy = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        when(core.findStatesByParcelAnyTenant(coreCopy)).thenReturn(Flux.just(state(deliveryId, "AT_RELAY_HUB", null)));
        when(rows.findById(deliveryId)).thenReturn(Mono.just(row(deliveryId)));

        StepVerifier.create(adapter.findByParcelId(coreCopy).map(Delivery::getId)).expectNext(deliveryId).verifyComplete();
        verify(rows, never()).findByParcelId(needPacket);
    }

    @Test
    @DisplayName("parcel lookup, deposit re-keyed by the migration on the need's own packet (not a core delivery parcel): found through the need")
    void parcelOfTheNeed_migratedDeposit() {
        UUID needPacket = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        when(core.findStatesByParcelAnyTenant(needPacket)).thenReturn(Flux.empty());
        when(rows.findByParcelId(needPacket)).thenReturn(Mono.just(row(deliveryId)));
        when(core.findStateAnyTenant(deliveryId)).thenReturn(Mono.just(state(deliveryId, "AT_RELAY_HUB", null)));

        StepVerifier.create(adapter.findByParcelId(needPacket).map(Delivery::getStatus))
                .expectNext(DeliveryStatus.AT_RELAY_HUB).verifyComplete();
    }

    @Test
    @DisplayName("parcel lookup: a core delivery that gofp does not extend is skipped, the next one is used")
    void parcelOfTheCore_skipsDeliveriesUnknownToGofp() {
        UUID parcel = UUID.randomUUID();
        UUID agencyOnly = UUID.randomUUID();
        UUID gofp = UUID.randomUUID();
        when(core.findStatesByParcelAnyTenant(parcel)).thenReturn(Flux.just(
                state(agencyOnly, "AT_RELAY_HUB", null), state(gofp, "AT_RELAY_HUB", null)));
        when(rows.findById(agencyOnly)).thenReturn(Mono.empty());
        when(rows.findById(gofp)).thenReturn(Mono.just(row(gofp)));

        StepVerifier.create(adapter.findByParcelId(parcel).map(Delivery::getId)).expectNext(gofp).verifyComplete();
    }

    @Test
    @DisplayName("save writes the extension row only and hands back the core facts the caller had")
    void saveKeepsCallerFacts() {
        UUID id = UUID.randomUUID();
        Delivery caller = row(id).readThrough(DeliveryStatus.IN_TRANSIT, courier, new BigDecimal("1000"),
                null, null, null, null, null, null);
        Delivery persisted = row(id);
        when(rows.save(any())).thenReturn(Mono.just(persisted));

        StepVerifier.create(adapter.save(caller)).assertNext(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
            assertThat(d.getTarif()).isEqualTo(1000.0);
        }).verifyComplete();
    }
}
