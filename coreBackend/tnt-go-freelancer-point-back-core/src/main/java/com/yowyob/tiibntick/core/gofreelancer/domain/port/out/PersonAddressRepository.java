package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.PersonAddress;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for person address persistence operations.
 */
public interface PersonAddressRepository {

    Mono<PersonAddress> save(PersonAddress personAddress);

    Mono<PersonAddress> findById(UUID id);

    Flux<PersonAddress> findByPersonId(UUID personId);

    Mono<PersonAddress> findByPersonIdAndType(UUID personId, AddressType type);

    Mono<Void> deleteById(UUID id);
}
