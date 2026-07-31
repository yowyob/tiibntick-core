package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.ContactUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ContactDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactUseCase contactUseCase;

    @GetMapping("/user/{userId}")
    public Flux<ContactDTO> getContactsByUserId(
            @PathVariable UUID userId,
            @RequestParam(required = false) String search) {
        return contactUseCase.searchContacts(userId, search);
    }

    @GetMapping("/{id}")
    public Mono<ContactDTO> getContactById(@PathVariable UUID id) {
        return contactUseCase.getContactById(id);
    }
}
