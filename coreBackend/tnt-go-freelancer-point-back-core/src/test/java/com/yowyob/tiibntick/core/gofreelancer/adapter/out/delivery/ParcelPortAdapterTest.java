package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.in.ParcelUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RegisterParcelCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.ParcelDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParcelPortAdapterTest {

    private final ParcelUseCase parcels = mock(ParcelUseCase.class);
    private final ParcelPortAdapter adapter = new ParcelPortAdapter(parcels,
            new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(
                    com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder.class));

    @Test
    @DisplayName("a described packet keeps every field: kg, cm, designation, thickness, flags, photo")
    void fullPacket() {
        PackageSpecification spec = ParcelPortAdapter.toSpecification(new ParcelDetails(null, " Shoes ", "A pair",
                2.5, 30.0, 20.0, 12.0, 4.0, true, false, "https://img/1.jpg"));
        assertThat(spec).isEqualTo(new PackageSpecification(2.5, 30, 20, 12, true, false, "A pair", "Shoes", 4.0));
    }

    @Test
    @DisplayName("missing or non-positive sizes take 1 kg / 10 cm, a non-positive thickness and a blank designation are dropped")
    void neutralValues() {
        PackageSpecification spec = ParcelPortAdapter.toSpecification(new ParcelDetails(null, "  ", null,
                null, 0.0, -2.0, null, 0.0, null, null, null));
        assertThat(spec).isEqualTo(new PackageSpecification(1.0, 10, 10, 10, false, false, null, null, null));
    }

    @Test
    @DisplayName("register sends the photo with the specification and returns the core parcel id")
    void register() {
        Parcel created = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, null));
        when(parcels.registerParcel(any())).thenReturn(Mono.just(created));

        StepVerifier.create(adapter.register(new ParcelDetails(null, "Box", null, 1.0, 10.0, 10.0, 10.0, null,
                        false, false, "https://img/2.jpg")))
                .expectNext(created.getId()).verifyComplete();

        ArgumentCaptor<RegisterParcelCommand> cmd = ArgumentCaptor.forClass(RegisterParcelCommand.class);
        verify(parcels).registerParcel(cmd.capture());
        assertThat(cmd.getValue().photoUrl()).isEqualTo("https://img/2.jpg");
        assertThat(cmd.getValue().specification().designation()).isEqualTo("Box");
    }

    @Test
    @DisplayName("find maps the core parcel back, id included; an unknown id is empty")
    void find() {
        Parcel parcel = Parcel.create(new PackageSpecification(2, 30, 20, 10, true, true, "desc", "Shoes", 3.0));
        parcel.attachPhoto("https://img/3.jpg");
        when(parcels.findParcelById(parcel.getId())).thenReturn(Mono.just(parcel));
        UUID unknown = UUID.randomUUID();
        when(parcels.findParcelById(unknown)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.find(parcel.getId()))
                .expectNext(new ParcelDetails(parcel.getId(), "Shoes", "desc", 2.0, 30.0, 20.0, 10.0, 3.0, true, true,
                        "https://img/3.jpg"))
                .verifyComplete();
        StepVerifier.create(adapter.find(unknown)).verifyComplete();
    }
}
