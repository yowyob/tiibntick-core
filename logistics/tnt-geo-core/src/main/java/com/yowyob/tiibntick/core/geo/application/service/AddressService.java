package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.IManageAddressUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IAddressRepository;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.Address;
import com.yowyob.tiibntick.core.geo.domain.model.AddressLink;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Application service of the address book ({@link IManageAddressUseCase}). */
@Service
public class AddressService implements IManageAddressUseCase {

    private final IAddressRepository repository;

    public AddressService(IAddressRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Address> create(UUID tenantId, AddressDetails d) {
        return Mono.fromSupplier(() -> Address.create(d.id(), tenantId, d.street(), d.landmark(), d.district(),
                        d.city(), d.region(), d.country(), d.postalCode(), d.latitude(), d.longitude()))
                .flatMap(repository::save);
    }

    @Override
    public Mono<Address> update(UUID tenantId, UUID addressId, AddressDetails d) {
        return findById(tenantId, addressId)
                .map(existing -> existing.withContent(d.street(), d.landmark(), d.district(), d.city(), d.region(),
                        d.country(), d.postalCode(), d.latitude(), d.longitude()))
                .flatMap(repository::save);
    }

    @Override
    public Mono<Void> delete(UUID tenantId, UUID addressId) {
        return repository.deleteById(tenantId, addressId);
    }

    @Override
    public Mono<Address> findById(UUID tenantId, UUID addressId) {
        return repository.findById(tenantId, addressId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("Address", addressId.toString())));
    }

    @Override
    public Flux<Address> findAll(UUID tenantId) {
        return repository.findAll(tenantId);
    }

    @Override
    public Flux<Address> search(UUID tenantId, String query) {
        if (query == null || query.isBlank()) {
            return Flux.empty();
        }
        return repository.searchByText(tenantId, query.trim());
    }

    @Override
    public Mono<AddressLink> link(UUID ownerId, UUID addressId, String label) {
        return Mono.fromSupplier(() -> AddressLink.create(ownerId, addressId, label))
                .flatMap(repository::saveLinkIfAbsent);
    }

    @Override
    public Flux<AddressLink> findLinksByOwner(UUID ownerId) {
        return repository.findLinksByOwner(ownerId);
    }

    @Override
    public Flux<AddressLink> findLinksByAddress(UUID addressId) {
        return repository.findLinksByAddress(addressId);
    }

    @Override
    public Mono<Void> unlink(UUID ownerId, UUID addressId) {
        return repository.deleteLinks(ownerId, addressId);
    }
}
