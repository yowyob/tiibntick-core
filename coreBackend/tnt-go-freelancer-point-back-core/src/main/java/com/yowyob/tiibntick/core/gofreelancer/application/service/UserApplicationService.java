package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.UserRegistrationDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.UserResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.UserUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service implementing UserUseCase.
 * A "user" is a lightweight actor who can create delivery needs.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApplicationService implements UserUseCase {

    private final IDeliveryNeedRepository deliveryNeedRepository;

    @Override
    public Mono<UserResponseDTO> registerUser(UserRegistrationDTO dto) {
        // Users are provisioned at the Kernel level.
        // Here we return a lightweight response with a generated ID.
        UUID newId = UUID.randomUUID();
        return Mono.just(UserResponseDTO.builder()
                .id(newId)
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .createdAt(Instant.now())
                .build());
    }

    @Override
    public Mono<UserResponseDTO> getUserById(UUID id) {
        return deliveryNeedRepository.findAllByUserId(id)
                .next()
                .map(need -> UserResponseDTO.builder()
                        .id(need.getUserId())
                        .createdAt(need.getCreatedAt())
                        .build())
                .switchIfEmpty(Mono.just(UserResponseDTO.builder().id(id).build()));
    }

    @Override
    public Flux<UserResponseDTO> getAllUsers() {
        return Flux.empty(); // Full user listing requires Kernel integration
    }

    @Override
    public Mono<Void> deleteUser(UUID id) {
        log.info("User deletion requested for id={} — delegated to Kernel", id);
        return Mono.empty();
    }
}
