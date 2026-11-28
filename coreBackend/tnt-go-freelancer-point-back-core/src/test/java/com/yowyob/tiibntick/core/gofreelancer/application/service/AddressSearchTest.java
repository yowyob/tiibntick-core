package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.geo.application.service.AddressService;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IGeolocationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The {@code /api/addresses} behaviour of {@link AddressApplicationService} on the real core address service
 * ({@code AddressService}) over an in-memory store: create / read / update / delete, text search, geocoding
 * fallback, usage type as a link label.
 */
class AddressSearchTest {

    private final InMemoryAddressBook book = new InMemoryAddressBook();
    private final IGeolocationPort geo = mock(IGeolocationPort.class);
    private final ResolveCurrentUserUseCase currentUser = mock(ResolveCurrentUserUseCase.class);
    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final AddressApplicationService service =
            new AddressApplicationService(new AddressService(book), geo, currentUser);

    AddressSearchTest() {
        as(user);
    }

    private void as(UUID userId) {
        TntSecurityContext ctx = mock(TntSecurityContext.class);
        when(ctx.tenantId()).thenReturn(tenant);
        when(ctx.userId()).thenReturn(userId);
        when(ctx.isFullyAuthenticated()).thenReturn(userId != null);
        when(currentUser.resolveCurrentContextOrAnonymous()).thenReturn(Mono.just(ctx));
    }

    private static AddressDTO dto(String street, String landmark, String city, AddressType type) {
        return AddressDTO.builder().type(type)
                .address(Address.builder().street(street).landmark(landmark).quarter("Bastos")
                        .city(city).country("CM").build())
                .build();
    }

    @Test
    void createReadUpdateDeleteGoThroughTheCoreAddressBook() {
        AddressDTO created = service.createAddress(dto("Rue 1", "Face pharmacie", "Yaounde", AddressType.HOME)).block();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getType()).isEqualTo(AddressType.HOME);
        assertThat(created.getAddress().getQuarter()).contains("Bastos");

        StepVerifier.create(service.getAddressById(created.getId()))
                .assertNext(a -> assertThat(a.getAddress().getLandmark()).contains("Face pharmacie")).verifyComplete();

        StepVerifier.create(service.updateAddress(created.getId(), dto("Rue 2", null, "Douala", AddressType.WORK)))
                .assertNext(a -> {
                    assertThat(a.getId()).isEqualTo(created.getId());
                    assertThat(a.getAddress().getCity()).isEqualTo("Douala");
                    assertThat(a.getType()).isEqualTo(AddressType.WORK);
                }).verifyComplete();

        StepVerifier.create(service.deleteAddress(created.getId())).verifyComplete();
        StepVerifier.create(service.getAddressById(created.getId())).verifyComplete();
        StepVerifier.create(service.updateAddress(created.getId(), dto("x", null, "Douala", null))).verifyComplete();
    }

    @Test
    void aGivenIdIsKept() {
        UUID id = UUID.randomUUID();
        AddressDTO in = dto("Rue 1", null, "Yaounde", null);
        in.setId(id);
        assertThat(service.createAddress(in).block().getId()).isEqualTo(id);
    }

    @Test
    void theTypeDefaultsToPrimaryForAnAuthenticatedCallerAndIsAbsentForAnAnonymousOne() {
        assertThat(service.createAddress(dto("Rue 1", null, "Yaounde", null)).block().getType())
                .isEqualTo(AddressType.PRIMARY);
        as(null);
        assertThat(service.createAddress(dto("Rue 2", null, "Yaounde", null)).block().getType()).isNull();
    }

    @Test
    void localSearchMatchesLandmarkOrStreetIgnoringCase() {
        service.createAddress(dto("Avenue Kennedy", null, "Yaounde", null)).block();
        service.createAddress(dto(null, "Carrefour KENNEDY", "Yaounde", null)).block();
        service.createAddress(dto("Rue Joss", null, "Douala", null)).block();

        StepVerifier.create(service.searchAddresses("kennedy").collectList())
                .assertNext(found -> assertThat(found).hasSize(2)).verifyComplete();
    }

    @Test
    void theCityOfTheFormIsUsedToGeocodeAndCarriedByTheSuggestion() {
        when(geo.getCoordinatesFromAddress("Bonanjo, Douala")).thenReturn(Mono.just(new double[] {4.05, 9.69}));

        StepVerifier.create(service.searchAddresses("Bonanjo", "Douala")).assertNext(dto -> {
            assertThat(dto.getAddress().getCity()).isEqualTo("Douala");
            assertThat(dto.getAddress().getCoordinates()).isPresent();
        }).verifyComplete();
        verify(geo).getCoordinatesFromAddress(eq("Bonanjo, Douala"));
    }

    @Test
    void withoutACityTheHistoricalDefaultStillApplies() {
        when(geo.getCoordinatesFromAddress("Mvan")).thenReturn(Mono.just(new double[] {3.8, 11.5}));
        StepVerifier.create(service.searchAddresses("Mvan"))
                .assertNext(dto -> assertThat(dto.getAddress().getCity()).isEqualTo("Yaoundé")).verifyComplete();
    }

    @Test
    void aGeocoderThatFailsGivesAnEmptyListNotAnError() {
        when(geo.getCoordinatesFromAddress(anyString())).thenReturn(Mono.error(new IllegalStateException("nominatim 429")));
        StepVerifier.create(service.searchAddresses("Mvan", "Yaoundé")).verifyComplete();
    }
}
