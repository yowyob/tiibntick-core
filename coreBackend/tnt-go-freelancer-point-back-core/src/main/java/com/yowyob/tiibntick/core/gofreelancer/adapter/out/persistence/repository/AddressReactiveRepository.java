package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity.AddressEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Read access to an address by id. Backed by the core address book ({@code tnt-geo-core}) through
 * {@code GeoCoreAddressReader}; the module no longer owns an address table. Empty when the address does not exist
 * (or is not visible to the current tenant).
 */
public interface AddressReactiveRepository {

    Mono<AddressEntity> findById(UUID id);
}
