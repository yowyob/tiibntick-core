package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ContactDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.ContactUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST adapter for user contacts.
 *
 * @author MANFOUO BRAUN
 */
@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactUseCase contactUseCase;

    @GetMapping("/user/{userId}")
    public Flux<ContactDTO> getContactsByUserId(
            @PathVariable UUID userId,
            @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return contactUseCase.searchContacts(userId, search);
        }
        return contactUseCase.getContactsByUserId(userId);
    }

    @GetMapping("/{id}")
    public Mono<ContactDTO> getContactById(@PathVariable UUID id) {
        return contactUseCase.getContactById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ContactDTO> createContact(@RequestBody ContactDTO request) {
        return contactUseCase.createContact(request);
    }

    @PutMapping("/{id}")
    public Mono<ContactDTO> updateContact(@PathVariable UUID id, @RequestBody ContactDTO request) {
        return contactUseCase.updateContact(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteContact(@PathVariable UUID id) {
        return contactUseCase.deleteContact(id);
    }
}
