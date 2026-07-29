package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Contact;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain ContactRepository port to the R2DBC
 * Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class ContactRepositoryAdapter implements ContactRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.ContactRepository r2dbcRepository;

    @Override
    public Mono<Contact> save(Contact contact) {
        return r2dbcRepository.save(contact);
    }

    @Override
    public Mono<Contact> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Mono<Contact> findByUserIdAndFirstNameIgnoreCaseAndLastNameIgnoreCase(UUID userId, String firstName, String lastName) {
        return r2dbcRepository.findByUserIdAndFirstNameIgnoreCaseAndLastNameIgnoreCase(userId, firstName, lastName);
    }

    @Override
    public Flux<Contact> findAllByUserId(UUID userId) {
        return r2dbcRepository.findAllByUserId(userId);
    }

    @Override
    public Flux<Contact> searchContacts(UUID userId, String searchTerm) {
        return r2dbcRepository.searchContacts(userId, searchTerm);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
