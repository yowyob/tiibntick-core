package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.UserUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.UserRegistrationDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.UserResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserUseCase userUseCase;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponseDTO> registerUser(@Valid @RequestBody UserRegistrationDTO dto) {
        return userUseCase.registerUser(dto);
    }

    @GetMapping("/{id}")
    public Mono<UserResponseDTO> getUser(@PathVariable UUID id) {
        return userUseCase.getUserById(id);
    }

    @GetMapping
    public Flux<UserResponseDTO> getAllUsers() {
        return userUseCase.getAllUsers();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteUser(@PathVariable UUID id) {
        return userUseCase.deleteUser(id);
    }
}
