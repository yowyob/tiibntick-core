package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.PersonAddress;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PersonAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain PersonAddressRepository port to the R2DBC
 * Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class PersonAddressRepositoryAdapter implements PersonAddressRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.PersonAddressRepository r2dbcRepository;

    @Override
    public Mono<PersonAddress> save(PersonAddress personAddress) {
        return r2dbcRepository.save(personAddress);
    }

    @Override
    public Mono<PersonAddress> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Flux<PersonAddress> findByPersonId(UUID personId) {
        return r2dbcRepository.findByPersonId(personId);
    }

    @Override
    public Mono<PersonAddress> findByPersonIdAndType(UUID personId, AddressType type) {
        return r2dbcRepository.findByPersonIdAndType(personId, type);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
