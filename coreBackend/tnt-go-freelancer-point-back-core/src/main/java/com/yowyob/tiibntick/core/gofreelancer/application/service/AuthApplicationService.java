package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.auth.application.port.in.ValidateTokenUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntTokenPair;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AuthRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AuthResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.UserRegistrationDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.UserStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.AuthUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpClientRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Application service implementing AuthUseCase.
 *
 * <p>Login, refresh and register are delegated to the Kernel (YowAuth0) via HTTP.
 * {@code me()} resolves the caller's profile from the local GofpUser store.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthApplicationService implements AuthUseCase {

    private final ValidateTokenUseCase validateTokenUseCase;
    private final WebClient.Builder    webClientBuilder;
    private final GofpUserRepository   gofpUserRepository;
    private final GofpClientRepository gofpClientRepository;
    private final PasswordHasherService passwordHasherService;

    @Value("${yow.auth.base-url:http://localhost:8080}")
    private String authBaseUrl;

    @Override
    public Mono<AuthResponseDTO> login(AuthRequestDTO request) {
        return webClientBuilder.build()
                .post()
                .uri(authBaseUrl + "/api/v1/auth/login")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AuthResponseDTO.class)
                .doOnError(e -> log.warn("Login failed for {}: {}", request.getEmail(), e.getMessage()));
    }

    @Override
    public Mono<Void> logout(String token) {
        return Mono.fromCallable(() -> validateTokenUseCase.isValid(token)).then();
    }

    @Override
    public Mono<AuthResponseDTO> register(UserRegistrationDTO request) {
        UUID coreUserId = UUID.randomUUID();

        GofpUser user = GofpUser.builder()
                .id(UUID.randomUUID())
                .coreUserId(coreUserId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .passwordHash(passwordHasherService.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .role("CLIENT")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        GofpClient client = GofpClient.builder()
                .id(UUID.randomUUID())
                .coreClientId(UUID.randomUUID())
                .coreUserId(coreUserId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return gofpUserRepository.save(user)
                .flatMap(savedUser -> gofpClientRepository.save(client))
                .flatMap(savedClient ->
                        webClientBuilder.build()
                                .post()
                                .uri(authBaseUrl + "/api/v1/auth/register")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(AuthResponseDTO.class)
                                .onErrorResume(e -> {
                                    log.error("Kernel registration failed for coreUserId={}: {}", coreUserId, e.getMessage());
                                    AuthResponseDTO partial = new AuthResponseDTO();
                                    partial.setId(coreUserId);
                                    partial.setFirstName(request.getFirstName());
                                    partial.setLastName(request.getLastName());
                                    partial.setUserType("CLIENT");
                                    return Mono.just(partial);
                                })
                )
                .doOnSuccess(r -> log.info("User registered: coreUserId={}", coreUserId));
    }

    @Override
    public Mono<TntTokenPair> refresh(String refreshToken) {
        return webClientBuilder.build()
                .post()
                .uri(authBaseUrl + "/api/v1/auth/refresh")
                .bodyValue(Map.of("refreshToken", refreshToken))
                .retrieve()
                .bodyToMono(TntTokenPair.class)
                .doOnError(e -> log.warn("Token refresh failed: {}", e.getMessage()));
    }

    @Override
    public Mono<AuthResponseDTO> me(TntUserIdentity currentUser) {
        return gofpUserRepository.findByCoreUserId(currentUser.userId())
                .map(user -> {
                    AuthResponseDTO dto = new AuthResponseDTO();
                    dto.setId(user.getCoreUserId());
                    dto.setFirstName(user.getFirstName());
                    dto.setLastName(user.getLastName());
                    dto.setEmail(user.getEmail());
                    dto.setPhone(user.getPhone());
                    dto.setIsActive(user.getIsActive());
                    dto.setRole(user.getRole());
                    dto.setUserType(user.getRole());
                    return dto;
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "User not found for userId: " + currentUser.userId())));
    }
}
