package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageAddressUseCase;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageAddressUseCase.AddressDetails;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.AddressLink;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IGeolocationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Addresses of the {@code /api/addresses} routes, kept in the core address book ({@code tnt-geo-core}).
 *
 * <p>The module owns no address table. The usage type of an address (PRIMARY, HOME, WORK...) is a link label of the
 * core, owned by the caller who set it: it is recorded for an authenticated caller and read back from the links of
 * the address (the caller's own first). The geocoding fallback of the search stays in the module's
 * {@link IGeolocationPort}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressApplicationService implements AddressUseCase {

    private static final String DEFAULT_TYPE = AddressType.PRIMARY.name();

    private final IManageAddressUseCase coreAddresses;
    private final IGeolocationPort geolocationPort;
    private final ResolveCurrentUserUseCase currentUser;

    /** Tenant of the current context and, when the caller is authenticated, the caller's user id. */
    private record Caller(UUID tenantId, UUID userId) { }

    private Mono<Caller> caller() {
        return currentUser.resolveCurrentContextOrAnonymous()
                .defaultIfEmpty(TntSecurityContext.anonymous())
                .map(ctx -> new Caller(ctx.tenantId() != null ? ctx.tenantId() : TenantContextHolder.SYSTEM_TENANT,
                        ctx.isFullyAuthenticated() ? ctx.userId() : null));
    }

    @Override
    public Mono<AddressDTO> createAddress(AddressDTO dto) {
        log.info("Création d'une nouvelle adresse");
        return caller().flatMap(c -> coreAddresses.create(c.tenantId(), toDetails(dto, dto.getId()))
                .flatMap(created -> recordType(c, created.id(), dto.getType()).thenReturn(created))
                .flatMap(created -> toDto(c, created)));
    }

    @Override
    public Mono<AddressDTO> getAddressById(UUID id) {
        return caller().flatMap(c -> coreAddresses.findById(c.tenantId(), id)
                .onErrorResume(GeoNotFoundException.class, e -> Mono.empty())
                .flatMap(a -> toDto(c, a)));
    }

    @Override
    public Flux<AddressDTO> getAllAddresses() {
        return caller().flatMapMany(c -> coreAddresses.findAll(c.tenantId())
                .concatMap(a -> toDto(c, a)));
    }

    @Override
    public Mono<AddressDTO> updateAddress(UUID id, AddressDTO dto) {
        log.info("Mise à jour de l'adresse {}", id);
        return caller().flatMap(c -> coreAddresses.update(c.tenantId(), id, toDetails(dto, id))
                .onErrorResume(GeoNotFoundException.class, e -> Mono.empty())
                .flatMap(updated -> recordType(c, updated.id(), dto.getType()).thenReturn(updated))
                .flatMap(updated -> toDto(c, updated)));
    }

    @Override
    public Mono<Void> deleteAddress(UUID id) {
        log.info("Suppression de l'adresse {}", id);
        return caller().flatMap(c -> coreAddresses.delete(c.tenantId(), id));
    }

    @Override
    public Flux<AddressDTO> searchAddresses(String query) {
        return searchAddresses(query, null);
    }

    @Override
    public Flux<AddressDTO> searchAddresses(String query, String city) {
        log.info("Recherche d'adresse pour : {} (ville : {})", query, city);
        boolean hasCity = city != null && !city.isBlank();

        Flux<AddressDTO> localResults = caller().flatMapMany(c -> coreAddresses.search(c.tenantId(), query)
                .concatMap(a -> toDto(c, a)));

        // En cas de liste vide, on fait un fallback vers le service de géolocalisation externe.
        // L'autocomplétion ne doit jamais échouer : un géocodeur lent ou en panne donne une liste vide.
        String geocoded = hasCity ? query + ", " + city.trim() : query;
        // Mono.defer: the geocoder is only called when nothing was found locally.
        return localResults.switchIfEmpty(Flux.defer(() ->
                geolocationPort.getCoordinatesFromAddress(geocoded)
                        .map(coords -> {
                            GeoCoordinates geo = GeoCoordinates.of(coords[0], coords[1]);
                            Address addressVO = Address.builder()
                                    .street(query)
                                    .landmark(query)
                                    .city(hasCity ? city.trim() : "Yaoundé")
                                    .country("CM")
                                    .coordinates(geo)
                                    .build();
                            return new AddressDTO(UUID.randomUUID(), addressVO, null);
                        })
                        .flux()
                        .onErrorResume(e -> {
                            log.warn("Géocodage de '{}' indisponible : {}", geocoded, e.toString());
                            return Flux.empty();
                        })));
    }

    // --- Usage type = link label ---

    /** Records the usage type as a link of the caller (PRIMARY when none is given). Anonymous: nothing to record. */
    private Mono<Void> recordType(Caller c, UUID addressId, AddressType type) {
        if (c.userId() == null) {
            return Mono.empty();
        }
        String label = type != null ? type.name() : DEFAULT_TYPE;
        return coreAddresses.unlink(c.userId(), addressId)
                .then(coreAddresses.link(c.userId(), addressId, label))
                .then();
    }

    private Mono<AddressType> typeOf(Caller c, UUID addressId) {
        return coreAddresses.findLinksByAddress(addressId)
                .collectList()
                .mapNotNull(links -> {
                    String label = links.stream()
                            .filter(l -> l.ownerId().equals(c.userId()))
                            .map(AddressLink::label).findFirst()
                            .or(() -> links.stream().map(AddressLink::label).findFirst())
                            .orElse(null);
                    return parseType(label);
                });
    }

    private static AddressType parseType(String label) {
        if (label == null) return null;
        try {
            return AddressType.valueOf(label);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // --- MAPPERS ---

    private static AddressDetails toDetails(AddressDTO dto, UUID id) {
        Address a = dto.getAddress();
        if (a == null) {
            throw new IllegalArgumentException("address is required");
        }
        return new AddressDetails(id,
                a.getStreet().orElse(null), a.getLandmark().orElse(null), a.getQuarter().orElse(null),
                a.getCity(), a.getRegion().orElse(null), a.getCountry(), a.getPostalCode().orElse(null),
                a.getCoordinates().map(GeoCoordinates::getLatitude).orElse(null),
                a.getCoordinates().map(GeoCoordinates::getLongitude).orElse(null));
    }

    private Mono<AddressDTO> toDto(Caller c, com.yowyob.tiibntick.core.geo.domain.model.Address entity) {
        GeoCoordinates coords = entity.latitude() != null && entity.longitude() != null
                ? GeoCoordinates.of(entity.latitude(), entity.longitude()) : null;
        Address vo = Address.builder()
                .street(entity.street())
                .landmark(entity.landmark())
                .quarter(entity.district())
                .city(entity.city())
                .country(entity.country())
                .region(entity.region())
                .postalCode(entity.postalCode())
                .coordinates(coords)
                .build();
        return typeOf(c, entity.id())
                .map(type -> new AddressDTO(entity.id(), vo, type))
                .defaultIfEmpty(new AddressDTO(entity.id(), vo, null));
    }
}
