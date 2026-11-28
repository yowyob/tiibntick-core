package com.yowyob.tiibntick.core.agency.org.hubops.application.service;

import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.HubParcelExpired;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.HubParcelRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.application.mapper.HubParcelMapper;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.HubParcelRecord;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.vo.ParcelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L8: the hub parcel expiry driven by the core's "not collected at the relay" parcel event. */
class HubParcelExpiryServiceTest {

    private final HubParcelRecordR2dbcRepository parcels = mock(HubParcelRecordR2dbcRepository.class);
    private final HubOccupancyService occupancy = mock(HubOccupancyService.class);
    private final AgencyEventPublisher events = mock(AgencyEventPublisher.class);
    private final HubParcelExpiryService service = new HubParcelExpiryService(parcels, occupancy, events);
    private final UUID tenant = UUID.randomUUID();
    private final UUID hub = UUID.randomUUID();

    private HubParcelRecord deposited() {
        return HubParcelRecord.deposit(UUID.randomUUID(), tenant, hub, UUID.randomUUID(), null, "TNT-1", 72,
                Instant.now(), null, null);
    }

    @Test
    @DisplayName("a deposited parcel is marked EXPIRED, frees its place and publishes HubParcelExpired")
    void expiresADepositedParcel() {
        when(parcels.findByTrackingCodeAndTenantId("TNT-1", tenant))
                .thenReturn(Mono.just(HubParcelMapper.toEntity(deposited())));
        when(parcels.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(occupancy.adjustOccupancy(tenant, hub, -1)).thenReturn(Mono.empty());
        when(events.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.expireDeposited("TNT-1", tenant, Instant.now()))
                .expectNextMatches(p -> p.getStatus() == ParcelStatus.EXPIRED)
                .verifyComplete();
        verify(occupancy).adjustOccupancy(eq(tenant), eq(hub), eq(-1));
        verify(events).publish(any(HubParcelExpired.class));
    }

    @Test
    @DisplayName("a replay (parcel already EXPIRED) or an unknown code changes nothing")
    void replayAndUnknownChangeNothing() {
        HubParcelRecord expired = deposited();
        expired.markExpired(Instant.now());
        when(parcels.findByTrackingCodeAndTenantId("TNT-1", tenant))
                .thenReturn(Mono.just(HubParcelMapper.toEntity(expired)));
        when(parcels.findByTrackingCodeAndTenantId("TNT-X", tenant)).thenReturn(Mono.empty());

        StepVerifier.create(service.expireDeposited("TNT-1", tenant, Instant.now())).verifyComplete();
        StepVerifier.create(service.expireDeposited("TNT-X", tenant, Instant.now())).verifyComplete();
        verify(parcels, never()).save(any());
        verify(events, never()).publish(any());
    }
}
