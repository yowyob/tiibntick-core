package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Contact;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for contact persistence operations.
 */
public interface ContactRepository {

    Mono<Contact> save(Contact contact);

    Mono<Contact> findById(UUID id);

    Mono<Contact> findByUserIdAndFirstNameIgnoreCaseAndLastNameIgnoreCase(UUID userId, String firstName, String lastName);

    Flux<Contact> findAllByUserId(UUID userId);

    Flux<Contact> searchContacts(UUID userId, String searchTerm);

    Mono<Void> deleteById(UUID id);
}
