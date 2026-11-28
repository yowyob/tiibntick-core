package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryAnnouncementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.ParcelUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NeedAnnouncementService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L1 (architecture-cible 3.2): the parcel registered with a delivery need keeps its id in the core
 * announcement the need publishes (and so in the delivery the core creates from it at selection). The
 * deposit side is covered by {@code RelayDepositRulesTest}, the core side by
 * {@code DeliveryAnnouncementServicePublishParcelTest}.
 */
class ParcelIdFollowsTheGoodsTest {

    private final UUID tenant = UUID.randomUUID();

    private static PublishAnnouncementPortCommand command(UUID parcelId) {
        return new PublishAnnouncementPortCommand(UUID.randomUUID(), UUID.randomUUID(), "t", "d", null, "XAF",
                "QUOTE_REQUEST", 2.0, 30, 20, 10, false, false, "Box", null, null, "R", "+237000000000")
                .withParcelId(parcelId);
    }

    @Test
    @DisplayName("need → announcement command: the need's parcel id is carried")
    void needPublishesItsParcel() {
        IDeliveryAnnouncementPort port = mock(IDeliveryAnnouncementPort.class);
        TenantContextHolder tenants = mock(TenantContextHolder.class);
        when(tenants.currentTenantId()).thenReturn(Mono.just(tenant));
        when(port.publish(any())).thenReturn(Mono.empty());
        when(port.findByOrigin(any(), any(), any())).thenReturn(Mono.empty());
        NeedAnnouncementService service = new NeedAnnouncementService(port, tenants);
        UUID parcel = UUID.randomUUID();
        DeliveryNeed need = new DeliveryNeed();
        need.setId(UUID.randomUUID());
        need.setUserId(UUID.randomUUID());
        need.setTitle("Shoes");
        need.setPacketId(parcel);

        service.publishFor(need, mock(Address.class), mock(Address.class), null).block();

        ArgumentCaptor<PublishAnnouncementPortCommand> cmd = ArgumentCaptor.forClass(PublishAnnouncementPortCommand.class);
        verify(port).publish(cmd.capture());
        assertThat(cmd.getValue().parcelId()).isEqualTo(parcel);
    }

    @Test
    @DisplayName("announcement → core: the registered parcel itself is handed to the core, not a copy")
    void coreAnnouncementReusesTheParcel() {
        DeliveryAnnouncementUseCase announcements = mock(DeliveryAnnouncementUseCase.class);
        ParcelUseCase parcels = mock(ParcelUseCase.class);
        DeliveryAnnouncementPortAdapter adapter =
                new DeliveryAnnouncementPortAdapter(announcements, mock(DeliveryQueryUseCase.class),
                        mock(com.yowyob.tiibntick.core.delivery.application.port.in.AnnouncementLookupUseCase.class));
        adapter.setParcels(parcels);
        Parcel registered = Parcel.create(new PackageSpecification(2, 30, 20, 10, false, false, "Box", "Shoes", null));
        when(parcels.findParcelById(registered.getId())).thenReturn(Mono.just(registered));
        when(announcements.publishAnnouncement(any())).thenReturn(Mono.empty());

        adapter.publish(command(registered.getId())).block();

        ArgumentCaptor<CreateDeliveryAnnouncementCommand> cmd = ArgumentCaptor.forClass(CreateDeliveryAnnouncementCommand.class);
        verify(announcements).publishAnnouncement(cmd.capture());
        assertThat(cmd.getValue().existingParcel()).isSameAs(registered);
    }

    @Test
    @DisplayName("no parcel id (announcement typed by hand): the core creates the parcel, nothing is looked up")
    void noParcelId() {
        DeliveryAnnouncementUseCase announcements = mock(DeliveryAnnouncementUseCase.class);
        ParcelUseCase parcels = mock(ParcelUseCase.class);
        DeliveryAnnouncementPortAdapter adapter =
                new DeliveryAnnouncementPortAdapter(announcements, mock(DeliveryQueryUseCase.class),
                        mock(com.yowyob.tiibntick.core.delivery.application.port.in.AnnouncementLookupUseCase.class));
        adapter.setParcels(parcels);
        when(announcements.publishAnnouncement(any())).thenReturn(Mono.empty());

        adapter.publish(command(null)).block();

        ArgumentCaptor<CreateDeliveryAnnouncementCommand> cmd = ArgumentCaptor.forClass(CreateDeliveryAnnouncementCommand.class);
        verify(announcements).publishAnnouncement(cmd.capture());
        assertThat(cmd.getValue().existingParcel()).isNull();
        verify(parcels, never()).findParcelById(any());
    }
}
