package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.Address;
import com.yowyob.tiibntick.core.geo.domain.model.AddressLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound port — persistence of {@link Address} and {@link AddressLink}. */
public interface IAddressRepository {

    /** Inserts or updates by id. */
    Mono<Address> save(Address address);

    /** The address when it exists and is visible to the tenant (its own, or tenant-less). */
    Mono<Address> findById(UUID tenantId, UUID id);

    Flux<Address> findAll(UUID tenantId);

    /** Text search (case-insensitive, substring) over street and landmark. */
    Flux<Address> searchByText(UUID tenantId, String text);

    Mono<Void> deleteById(UUID tenantId, UUID id);

    Mono<AddressLink> saveLinkIfAbsent(AddressLink link);

    Flux<AddressLink> findLinksByOwner(UUID ownerId);

    Flux<AddressLink> findLinksByAddress(UUID addressId);

    Mono<Void> deleteLinks(UUID ownerId, UUID addressId);
}
