package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ContactDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.ContactUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.ContactRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Contact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Contact use-case backed by the existing R2DBC {@link ContactRepository}.
 *
 * @author MANFOUO BRAUN
 */
@Service
@RequiredArgsConstructor
public class ContactUseCaseImpl implements ContactUseCase {

    private final ContactRepository contactRepository;

    @Override
    public Flux<ContactDTO> getContactsByUserId(UUID userId) {
        return contactRepository.findAllByUserId(userId).map(this::toDto);
    }

    @Override
    public Flux<ContactDTO> searchContacts(UUID userId, String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return getContactsByUserId(userId);
        }
        return contactRepository.searchContacts(userId, searchTerm).map(this::toDto);
    }

    @Override
    public Mono<ContactDTO> getContactById(UUID id) {
        return contactRepository.findById(id).map(this::toDto);
    }

    @Override
    public Mono<ContactDTO> createContact(ContactDTO request) {
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setUserId(request.getUserId());
        contact.setFirstName(request.getFirstName());
        contact.setLastName(request.getLastName());
        contact.setEmail(request.getEmail());
        contact.setPhone(request.getPhone());
        return contactRepository.save(contact).map(this::toDto);
    }

    @Override
    public Mono<ContactDTO> updateContact(UUID id, ContactDTO request) {
        return contactRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Contact not found: " + id)))
                .flatMap(existing -> {
                    if (request.getFirstName() != null) existing.setFirstName(request.getFirstName());
                    if (request.getLastName() != null) existing.setLastName(request.getLastName());
                    if (request.getEmail() != null) existing.setEmail(request.getEmail());
                    if (request.getPhone() != null) existing.setPhone(request.getPhone());
                    return contactRepository.save(existing);
                })
                .map(this::toDto);
    }

    @Override
    public Mono<Void> deleteContact(UUID id) {
        return contactRepository.deleteById(id);
    }

    private ContactDTO toDto(Contact c) {
        ContactDTO dto = new ContactDTO();
        dto.setId(c.getId());
        dto.setUserId(c.getUserId());
        dto.setFirstName(c.getFirstName());
        dto.setLastName(c.getLastName());
        dto.setEmail(c.getEmail());
        dto.setPhone(c.getPhone());
        return dto;
    }
}
