package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ContactDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for contact CRUD.
 *
 * @author MANFOUO BRAUN
 */
public interface ContactUseCase {
    Flux<ContactDTO> getContactsByUserId(UUID userId);

    Flux<ContactDTO> searchContacts(UUID userId, String searchTerm);

    Mono<ContactDTO> getContactById(UUID id);

    Mono<ContactDTO> createContact(ContactDTO request);

    Mono<ContactDTO> updateContact(UUID id, ContactDTO request);

    Mono<Void> deleteContact(UUID id);
}
