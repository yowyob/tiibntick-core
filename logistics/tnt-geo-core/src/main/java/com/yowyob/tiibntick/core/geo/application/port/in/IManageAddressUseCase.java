package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.Address;
import com.yowyob.tiibntick.core.geo.domain.model.AddressLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port — persisted addresses and their links to owners.
 *
 * <p>Complements {@link IGeocodeUseCase} (text to coordinates, no storage): this is the address book. Every method
 * is scoped to a tenant; an address stored without a tenant (migrated data) is visible to every tenant.
 */
public interface IManageAddressUseCase {

    /**
     * Content of an address to create or to replace. {@code id} is only read by {@code create} (a caller that
     * already knows the id, or a migration, keeps it); {@code update} ignores it.
     */
    record AddressDetails(UUID id, String street, String landmark, String district, String city, String region,
                          String country, String postalCode, Double latitude, Double longitude) { }

    Mono<Address> create(UUID tenantId, AddressDetails details);

    /** Replaces the content of an address; the id, tenant and creation date are kept. Fails with not-found. */
    Mono<Address> update(UUID tenantId, UUID addressId, AddressDetails details);

    /** Deletes the address and its links. Completes silently when it does not exist. */
    Mono<Void> delete(UUID tenantId, UUID addressId);

    Mono<Address> findById(UUID tenantId, UUID addressId);

    Flux<Address> findAll(UUID tenantId);

    /**
     * Case-insensitive text search over the street and the landmark, best-effort ordered by city then street.
     * A blank query matches nothing (use {@link #findAll}).
     */
    Flux<Address> search(UUID tenantId, String query);

    /** Links an owner to an address under a label. Linking the same (owner, address, label) twice is a no-op. */
    Mono<AddressLink> link(UUID ownerId, UUID addressId, String label);

    Flux<AddressLink> findLinksByOwner(UUID ownerId);

    /** Every link that points at the address, oldest first. */
    Flux<AddressLink> findLinksByAddress(UUID addressId);

    /** Removes every link between the owner and the address. */
    Mono<Void> unlink(UUID ownerId, UUID addressId);
}
