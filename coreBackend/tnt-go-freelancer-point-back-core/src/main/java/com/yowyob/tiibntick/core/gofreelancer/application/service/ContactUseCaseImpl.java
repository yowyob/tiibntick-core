package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.ContactUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ContactDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactUseCaseImpl implements ContactUseCase {

    @Override public Flux<ContactDTO> getContactsByUserId(UUID userId) { return Flux.empty(); /* TODO: implement */ }
    @Override public Flux<ContactDTO> searchContacts(UUID userId, String search) { return Flux.empty(); /* TODO: implement */ }
    @Override public Mono<ContactDTO> getContactById(UUID id) { return Mono.empty(); /* TODO: implement */ }
}
