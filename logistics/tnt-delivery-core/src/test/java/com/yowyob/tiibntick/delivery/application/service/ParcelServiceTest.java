package com.yowyob.tiibntick.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.RegisterParcelCommand;
import com.yowyob.tiibntick.core.delivery.application.port.out.ParcelRepository;
import com.yowyob.tiibntick.core.delivery.application.service.ParcelService;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelServiceTest {

    @Mock private ParcelRepository parcelRepository;
    @Mock private com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher events;

    @Test
    @DisplayName("registerParcel persists a parcel with the given specification and photo")
    void registersAndPersists() {
        ParcelService service = new ParcelService(parcelRepository, events);
        when(parcelRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(events.publishAll(any())).thenReturn(Mono.empty());
        PackageSpecification spec = new PackageSpecification(
                4.0, 30, 20, 50, false, true, "Cheese", "Fromage", 12.0);

        StepVerifier.create(service.registerParcel(new RegisterParcelCommand(spec, "https://f/x.jpg")))
                .assertNext(parcel -> {
                    assertThat(parcel.getId()).isNotNull();
                    assertThat(parcel.getSpecification()).isEqualTo(spec);
                    assertThat(parcel.getPhotoUrl()).isEqualTo("https://f/x.jpg");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("registerParcel without photo leaves the photo empty")
    void registersWithoutPhoto() {
        ParcelService service = new ParcelService(parcelRepository, events);
        when(parcelRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(events.publishAll(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.registerParcel(new RegisterParcelCommand(
                        new PackageSpecification(1, 10, 10, 10, false, false, null), "  ")))
                .assertNext(parcel -> assertThat(parcel.getPhotoUrl()).isNull())
                .verifyComplete();
    }

    @Test
    @DisplayName("P1: ParcelCreated is published once, after the save, with the tenant; without tenant, with the "
            + "system tenant (D-150)")
    void publishesParcelCreatedWithTheTenantOrTheSystemTenant() {
        ParcelService service = new ParcelService(parcelRepository, events);
        when(parcelRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(events.publishAll(any())).thenReturn(Mono.empty());
        UUID tenant = UUID.randomUUID();
        PackageSpecification spec = new PackageSpecification(1, 10, 10, 10, false, false, null);

        Parcel parcel = service.registerParcel(new RegisterParcelCommand(spec, null, tenant, null)).block();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.List<com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent>>
                published = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(events).publishAll(published.capture());
        assertThat(published.getValue()).singleElement().satisfies(e -> {
            var ae = (com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent) e;
            assertThat(ae.eventType()).isEqualTo("ParcelCreated");
            assertThat(ae.row()).isEqualTo("P1");
            assertThat(ae.tenantId()).isEqualTo(tenant);
            assertThat(ae.aggregateId()).isEqualTo(parcel.getId());
        });
        assertThat(parcel.getDomainEvents()).isEmpty();

        org.mockito.Mockito.clearInvocations(events);
        service.registerParcel(new RegisterParcelCommand(spec, null)).block();
        verify(events).publishAll(published.capture());
        assertThat(published.getValue()).singleElement().satisfies(e -> {
            var ae = (com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent) e;
            assertThat(ae.eventType()).isEqualTo("ParcelCreated");
            assertThat(ae.tenantId()).isEqualTo(com.yowyob.tiibntick.common.tenant.SystemTenant.id());
        });
    }

    @Test
    @DisplayName("findParcelById delegates to the repository, and is empty for a null id")
    void findsById() {
        ParcelService service = new ParcelService(parcelRepository, events);
        UUID id = UUID.randomUUID();
        Parcel parcel = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, null));
        when(parcelRepository.findById(id)).thenReturn(Mono.just(parcel));

        StepVerifier.create(service.findParcelById(id)).expectNext(parcel).verifyComplete();
        StepVerifier.create(service.findParcelById(null)).verifyComplete();
        verify(parcelRepository).findById(id);
    }
}
