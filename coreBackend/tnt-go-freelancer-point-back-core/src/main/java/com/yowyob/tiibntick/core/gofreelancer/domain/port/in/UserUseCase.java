package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.UserRegistrationDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.UserResponseDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserUseCase {
    Mono<UserResponseDTO> registerUser(UserRegistrationDTO dto);
    Mono<UserResponseDTO> getUserById(UUID id);
    Flux<UserResponseDTO> getAllUsers();
    Mono<Void> deleteUser(UUID id);
}
