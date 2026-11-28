package com.yowyob.tiibntick.delivery.application.service;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor;
import com.yowyob.tiibntick.core.delivery.application.service.ReturnDeliveryService;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementCreatorRole;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelReturnCause;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.ParcelCustody;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D-148: a RETURN delivery cancelled before pickup keeps the parcel {@code RETURN_REQUESTED}, publishes a parcel event
 * and opens a new return draft for the sender. D-150: no parcel event is ever produced without a tenant.
 */
class ReturnCancelledAndParcelTenantTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SENDER = UUID.randomUUID();

    @Test
    @DisplayName("D-148: return cancelled before pickup -> parcel stays RETURN_REQUESTED, event, new draft")
    void returnCancelledBeforePickupOpensANewDraft() {
        Parcel parcel = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, "Box"), TENANT, SENDER,
                TntEventActor.of(TntEventActor.Type.SENDER, SENDER));
        Delivery outbound = newDelivery(parcel, DeliveryKind.OUTBOUND, null);
        outbound.assignDeliveryPerson(UUID.randomUUID(), null, 3.0, null);
        outbound.confirmPickup();
        outbound.cancel("sender changed their mind");
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.RETURN_REQUESTED);

        Delivery ret = newDelivery(parcel, DeliveryKind.RETURN, outbound.getId());
        ret.assignDeliveryPerson(UUID.randomUUID(), null, 3.0, null);
        parcel.clearDomainEvents();
        ret.clearDomainEvents();
        ret.cancel("courier unavailable");

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.RETURN_REQUESTED);
        assertThat(parcel.getReturnCause()).isEqualTo(ParcelReturnCause.RETURN_DELIVERY_CANCELLED);
        assertThat(parcel.getFailedReturnCount()).isEqualTo(1);
        assertThat(parcel.getDomainEvents()).filteredOn(AggregateEvent.class::isInstance)
                .map(e -> (AggregateEvent) e)
                .singleElement().satisfies(e -> {
                    assertThat(e.eventType()).isEqualTo("ParcelReturnDeliveryCancelled");
                    assertThat(e.isTransition()).isFalse();
                    assertThat(e.tenantId()).isEqualTo(TENANT);
                    assertThat(e.details()).containsEntry("returnCause", "RETURN_DELIVERY_CANCELLED")
                            .containsEntry("failedReturnCount", 1);
                });

        DeliveryRepository deliveries = mock(DeliveryRepository.class);
        DeliveryAnnouncementRepository announcements = mock(DeliveryAnnouncementRepository.class);
        DeliveryThresholdsPort thresholds = mock(DeliveryThresholdsPort.class);
        List<DeliveryDomainEvent> published = new ArrayList<>();
        DeliveryEventPublisher events = new DeliveryEventPublisher() {
            @Override public Mono<Void> publish(DeliveryDomainEvent event) { published.add(event); return Mono.empty(); }
            @Override public Mono<Void> publishAll(List<DeliveryDomainEvent> list) {
                published.addAll(list);
                return Mono.empty();
            }
        };
        when(deliveries.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(announcements.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(thresholds.forAgency(any())).thenReturn(Mono.just(new DeliveryThresholdsPort.Thresholds(3, 3, 1)));
        DeliveryTransitionExecutor executor = new DeliveryTransitionExecutor(deliveries, announcements, events,
                new ReturnDeliveryService(announcements, thresholds));

        executor.saveAndPublish(ret).block();

        org.mockito.ArgumentCaptor<DeliveryAnnouncement> draft =
                org.mockito.ArgumentCaptor.forClass(DeliveryAnnouncement.class);
        org.mockito.Mockito.verify(announcements).save(draft.capture());
        assertThat(draft.getValue().getKind()).isEqualTo(AnnouncementKind.RETURN);
        assertThat(draft.getValue().getClientId()).as("the sender pays (D-147)").isEqualTo(SENDER);
        assertThat(draft.getValue().getCreatorRole()).isEqualTo(AnnouncementCreatorRole.CLIENT);
        assertThat(published).filteredOn(AggregateEvent.class::isInstance)
                .map(e -> ((AggregateEvent) e).eventType())
                .contains("DeliveryCancelled", "ParcelReturnDeliveryCancelled", "AnnouncementDraft");
    }

    @Test
    @DisplayName("D-150: a parcel registered or changed without tenant belongs to the system tenant")
    void noParcelEventWithoutTenant() {
        Parcel noTenant = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, "Box"));
        assertThat(noTenant.getDomainEvents()).filteredOn(AggregateEvent.class::isInstance)
                .map(e -> ((AggregateEvent) e).tenantId())
                .containsExactly(SystemTenant.id());

        Parcel explicitNull = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, "Box"), null,
                SENDER, TntEventActor.system());
        explicitNull.clearDomainEvents();
        explicitNull.changeCustody(ParcelCustody.courier(UUID.randomUUID()), "HANDOVER_COMPLETED", "HANDOVER",
                TntEventActor.system(), null, null);
        assertThat(explicitNull.getDomainEvents()).filteredOn(AggregateEvent.class::isInstance)
                .map(e -> ((AggregateEvent) e).tenantId())
                .containsExactly(SystemTenant.id());

        // a parcel read back from the database has no operation tenant: its next event still has one
        Parcel reloaded = Parcel.builder().id(UUID.randomUUID()).trackingCode("TNT-20261126-ABCDEFGH")
                .specification(new PackageSpecification(1, 10, 10, 10, false, false, "Box"))
                .status(ParcelStatus.CREATED).custody(ParcelCustody.sender(SENDER))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        reloaded.changeCustody(ParcelCustody.courier(UUID.randomUUID()), "HANDOVER_COMPLETED", "HANDOVER",
                TntEventActor.system(), null, null);
        assertThat(reloaded.getDomainEvents()).filteredOn(AggregateEvent.class::isInstance)
                .map(e -> ((AggregateEvent) e).tenantId())
                .containsExactly(SystemTenant.id());
    }

    private static Delivery newDelivery(Parcel parcel, DeliveryKind kind, UUID parent) {
        return Delivery.create(TENANT, null, SENDER, parcel, address(), address(),
                new RecipientInfo("Awa Ndiaye", "+237690000001", null), DeliveryUrgency.STANDARD, null, null, kind,
                parent, 3);
    }

    private static DeliveryAddress address() {
        return new DeliveryAddress(null, "Carrefour", "Bastos", "Yaoundé", "CM", new GeoCoordinates(3.88, 11.52));
    }
}
