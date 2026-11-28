package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.geo.application.port.out.IAddressRepository;
import com.yowyob.tiibntick.core.geo.domain.model.Address;
import com.yowyob.tiibntick.core.geo.domain.model.AddressLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory stand-in of the core address book storage (same tenant rule and text search as the SQL one). */
public class InMemoryAddressBook implements IAddressRepository {

    private final Map<UUID, Address> addresses = new ConcurrentHashMap<>();
    private final List<AddressLink> links = new CopyOnWriteArrayList<>();

    private static boolean visible(UUID tenantId, Address a) {
        return a.tenantId() == null || a.tenantId().equals(tenantId);
    }

    @Override public Mono<Address> save(Address address) {
        addresses.put(address.id(), address);
        return Mono.just(address);
    }

    @Override public Mono<Address> findById(UUID tenantId, UUID id) {
        return Mono.justOrEmpty(addresses.get(id)).filter(a -> visible(tenantId, a));
    }

    @Override public Flux<Address> findAll(UUID tenantId) {
        return Flux.fromIterable(addresses.values()).filter(a -> visible(tenantId, a));
    }

    @Override public Flux<Address> searchByText(UUID tenantId, String text) {
        String needle = text.toLowerCase();
        return findAll(tenantId).filter(a ->
                (a.landmark() != null && a.landmark().toLowerCase().contains(needle))
                        || (a.street() != null && a.street().toLowerCase().contains(needle)));
    }

    @Override public Mono<Void> deleteById(UUID tenantId, UUID id) {
        Address a = addresses.get(id);
        if (a != null && visible(tenantId, a)) {
            addresses.remove(id);
            links.removeIf(l -> l.addressId().equals(id));
        }
        return Mono.empty();
    }

    @Override public Mono<AddressLink> saveLinkIfAbsent(AddressLink link) {
        return Mono.just(links.stream()
                .filter(l -> l.ownerId().equals(link.ownerId()) && l.addressId().equals(link.addressId())
                        && Objects.equals(l.label(), link.label()))
                .findFirst().orElseGet(() -> { links.add(link); return link; }));
    }

    @Override public Flux<AddressLink> findLinksByOwner(UUID ownerId) {
        return Flux.fromIterable(links).filter(l -> l.ownerId().equals(ownerId));
    }

    @Override public Flux<AddressLink> findLinksByAddress(UUID addressId) {
        return Flux.fromIterable(links).filter(l -> l.addressId().equals(addressId));
    }

    @Override public Mono<Void> deleteLinks(UUID ownerId, UUID addressId) {
        links.removeIf(l -> l.ownerId().equals(ownerId) && l.addressId().equals(addressId));
        return Mono.empty();
    }
}
