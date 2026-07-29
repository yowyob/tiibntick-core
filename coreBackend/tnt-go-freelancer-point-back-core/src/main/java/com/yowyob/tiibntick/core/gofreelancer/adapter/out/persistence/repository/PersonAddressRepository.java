package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.PersonAddress;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for PersonAddress entity operations.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
@Repository
public interface PersonAddressRepository extends ReactiveCrudRepository<PersonAddress, UUID> {

    Flux<PersonAddress> findByPersonId(UUID personId);

    Mono<PersonAddress> findByPersonIdAndType(UUID personId, AddressType type);
}
